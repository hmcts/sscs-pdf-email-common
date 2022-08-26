package uk.gov.hmcts.reform.sscs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static uk.gov.hmcts.reform.sscs.ccd.util.CaseDataUtils.buildCaseData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import uk.gov.hmcts.reform.pdf.service.client.PDFServiceClient;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.docmosis.domain.Pdf;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.model.LetterType;

@RunWith(JUnitParamsRunner.class)
public class CcdNotificationsPdfServiceTest {

    private static final long CASE_ID = 123L;

    @InjectMocks
    private CcdNotificationsPdfService service;

    @Mock
    PdfStoreService pdfStoreService;

    @Mock
    CcdService ccdService;

    @Mock
    PDFServiceClient pdfServiceClient;

    @Mock
    IdamService idamService;

    SscsCaseData caseData;

    @Captor
    private ArgumentCaptor<SscsCaseData> caseDataCaptor;
    private AutoCloseable autoCloseable;
    private Correspondence correspondenceEmail;
    private Correspondence correspondenceLetter;
    private DocumentLink documentLink;
    private Correspondence existingCorrespondence;

    @Before
    public void setup() {
        autoCloseable = openMocks(this);

        caseData = buildCaseData().toBuilder()
            .ccdCaseId(String.valueOf(CASE_ID))
            .build();

        when(idamService.getIdamTokens()).thenReturn(IdamTokens.builder().build());

        SscsCaseDetails caseDetails = SscsCaseDetails.builder().data(caseData).build();
        when(ccdService.updateCaseWithoutRetry(any(), any(), any(), any(), any(), any())).thenReturn(caseDetails);
        when(ccdService.getByCaseId(CASE_ID, IdamTokens.builder().build())).thenReturn(caseDetails);

        when(pdfServiceClient.generateFromHtml(any(), any())).thenReturn("bytes".getBytes());

        documentLink = DocumentLink.builder()
            .documentUrl("aUrl")
            .documentBinaryUrl("aUrl/binary")
            .build();
        List<SscsDocument> sscsDocuments = new ArrayList<>(List.of(
            SscsDocument.builder()
                .value(SscsDocumentDetails.builder()
                    .documentFileName("Test.jpg")
                    .documentLink(documentLink)
                    .build())
                .build())
        );

        when(pdfStoreService.store(any(), any(), eq("dl6"))).thenReturn(sscsDocuments);
        when(pdfStoreService.store(any(), any(), eq(CorrespondenceType.Letter.name()))).thenReturn(sscsDocuments);

        correspondenceEmail = Correspondence.builder()
            .value(CorrespondenceDetails.builder()
                .sentOn("22 Jan 2021 11:00")
                .from("from")
                .to("to")
                .body("the body")
                .subject("a subject")
                .eventType("event")
                .correspondenceType(CorrespondenceType.Email)
                .build())
            .build();

        correspondenceLetter = Correspondence.builder()
            .value(CorrespondenceDetails.builder()
                .sentOn("22 Jan 2021 11:33")
                .from("from")
                .to("to")
                .subject("a subject")
                .eventType("event")
                .documentLink(DocumentLink.builder()
                    .documentUrl("Http://document")
                    .documentFilename("evidence-document.pdf")
                    .build())
                .correspondenceType(CorrespondenceType.Letter)
                .reasonableAdjustmentStatus(ReasonableAdjustmentStatus.REQUIRED)
                .build())
            .build();

        existingCorrespondence = Correspondence.builder()
            .value(CorrespondenceDetails.builder()
                .sentOn("22 Oct 2020 11:33")
                .documentLink(DocumentLink.builder()
                    .documentUrl("Testurl")
                    .build())
                .build())
            .build();
    }

    @After
    public void after() throws Exception {
        autoCloseable.close();
    }

    @Test
    public void mergeCorrespondenceIntoCcd() {
        service.mergeCorrespondenceIntoCcd(caseData, correspondenceEmail);
        verify(pdfServiceClient).generateFromHtml(any(), any());
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:00.pdf"), eq(CorrespondenceType.Email.name()));
        verify(ccdService).updateCaseWithoutRetry(any(), any(), any(),
            contains("Email Notification Successfully Sent"),
            contains("The Email Notification for the event event was Successfully Sent"),
            any());
    }

    @Test
    public void mergeLetterCorrespondenceIntoCcd() {
        byte[] bytes = "String".getBytes();

        service.mergeLetterCorrespondenceIntoCcd(bytes, CASE_ID, correspondenceEmail);
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:00.pdf"), eq(CorrespondenceType.Email.name()));
        verify(ccdService).updateCaseWithoutRetry(any(), any(), any(),
            contains("Email Notification Successfully Sent"),
            contains("The Email Notification for the event event was Successfully Sent"),
            any());
    }

    @Test
    @Parameters({"APPELLANT", "REPRESENTATIVE", "APPOINTEE", "JOINT_PARTY", "OTHER_PARTY"})
    public void givenAReasonableAdjustmentPdfForALetterType_thenCreateReasonableAdjustmentsCorrespondenceIntoCcdForRelevantParty(LetterType letterType) {
        List<Pdf> pdfs = Collections.singletonList(new Pdf("String".getBytes(), "adocument"));

        service.mergeReasonableAdjustmentsCorrespondenceIntoCcd(pdfs, CASE_ID, correspondenceLetter, letterType);
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:33.pdf"), eq(CorrespondenceType.Letter.name()));
        verify(ccdService).updateCaseWithoutRetry(caseDataCaptor.capture(), any(), any(),
            eq("Notification sent, Stopped bulk print"),
            eq("Notification sent, Stopped for reasonable adjustment to be sent"),
            any());

        List<Correspondence> results =
            findLettersToCaptureByParty(caseDataCaptor.getValue().getReasonableAdjustmentsLetters(), letterType);

        assertThat(results)
            .hasSize(1)
            .extracting(Correspondence::getValue)
            .allSatisfy(details -> {
                assertThat(details.getDocumentLink()).isEqualTo(documentLink);
                assertThat(details.getReasonableAdjustmentStatus()).isEqualTo(ReasonableAdjustmentStatus.REQUIRED);
            });
    }

    @Test
    public void givenAReasonableAdjustmentPdfForALetterTypeWithExistingReasonableAdjustments_thenAppendReasonableAdjustmentsCorrespondenceIntoCcd() {

        caseData.setReasonableAdjustmentsLetters(ReasonableAdjustmentsLetters.builder()
            .appellant(new ArrayList<>(List.of(existingCorrespondence)))
            .build());

        List<Pdf> pdfs = Collections.singletonList(new Pdf("String".getBytes(), "adocument"));

        service.mergeReasonableAdjustmentsCorrespondenceIntoCcd(pdfs, CASE_ID, correspondenceLetter, LetterType.APPELLANT);
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:33.pdf"), eq(CorrespondenceType.Letter.name()));
        verify(ccdService).updateCaseWithoutRetry(caseDataCaptor.capture(), any(), any(),
            eq("Notification sent, Stopped bulk print"),
            eq("Notification sent, Stopped for reasonable adjustment to be sent"),
            any());

        List<Correspondence> results = caseDataCaptor.getValue().getReasonableAdjustmentsLetters().getAppellant();

        assertThat(results)
            .hasSize(2)
            .contains(existingCorrespondence)
            .extracting(Correspondence::getValue)
            .anySatisfy(details -> {
                assertThat(details.getDocumentLink()).isEqualTo(documentLink);
                assertThat(details.getReasonableAdjustmentStatus()).isEqualTo(ReasonableAdjustmentStatus.REQUIRED);
            });
    }

    @Test
    @Parameters({"APPELLANT", "REPRESENTATIVE", "APPOINTEE", "JOINT_PARTY", "OTHER_PARTY"})
    public void givenAReasonableAdjustmentBytesForALetterType_thenCreateReasonableAdjustmentsCorrespondenceIntoCcdForRelevantParty(LetterType letterType) {
        byte[] bytes = "String".getBytes();

        service.mergeReasonableAdjustmentsCorrespondenceIntoCcd(bytes, CASE_ID, correspondenceLetter, letterType);
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:33.pdf"), eq(CorrespondenceType.Letter.name()));
        verify(ccdService).updateCaseWithoutRetry(caseDataCaptor.capture(), any(), any(),
            eq("Notification sent, Stopped bulk print"),
            eq("Notification sent, Stopped for reasonable adjustment to be sent"),
            any());

        List<Correspondence> results =
            findLettersToCaptureByParty(caseDataCaptor.getValue().getReasonableAdjustmentsLetters(), letterType);

        assertThat(results)
            .hasSize(1)
            .extracting(Correspondence::getValue)
            .allSatisfy(details -> {
                assertThat(details.getDocumentLink()).isEqualTo(documentLink);
                assertThat(details.getReasonableAdjustmentStatus()).isEqualTo(ReasonableAdjustmentStatus.REQUIRED);
            });
    }

    @Test
    public void givenAReasonableAdjustmentBytesForALetterTypeWithExistingReasonableAdjustments_thenAppendReasonableAdjustmentsCorrespondenceIntoCcd() {

        caseData.setReasonableAdjustmentsLetters(ReasonableAdjustmentsLetters.builder()
            .appellant(new ArrayList<>(List.of(existingCorrespondence)))
            .build());

        byte[] bytes = "String".getBytes();

        service.mergeReasonableAdjustmentsCorrespondenceIntoCcd(bytes, CASE_ID, correspondenceLetter, LetterType.APPELLANT);
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:33.pdf"), eq(CorrespondenceType.Letter.name()));
        verify(ccdService).updateCaseWithoutRetry(caseDataCaptor.capture(), any(), any(),
            eq("Notification sent, Stopped bulk print"),
            eq("Notification sent, Stopped for reasonable adjustment to be sent"),
            any());

        List<Correspondence> results = caseDataCaptor.getValue().getReasonableAdjustmentsLetters().getAppellant();

        assertThat(results)
            .hasSize(2)
            .contains(existingCorrespondence)
            .extracting(Correspondence::getValue)
            .anySatisfy(details -> {
                assertThat(details.getDocumentLink()).isEqualTo(documentLink);
                assertThat(details.getReasonableAdjustmentStatus()).isEqualTo(ReasonableAdjustmentStatus.REQUIRED);
            });
    }


    private List<Correspondence> findLettersToCaptureByParty(ReasonableAdjustmentsLetters reasonableAdjustmentsLetters, LetterType letterType) {
        if (LetterType.APPELLANT.equals(letterType)) {
            return reasonableAdjustmentsLetters.getAppellant();
        } else if (LetterType.APPOINTEE.equals(letterType)) {
            return reasonableAdjustmentsLetters.getAppointee();
        } else if (LetterType.REPRESENTATIVE.equals(letterType)) {
            return reasonableAdjustmentsLetters.getRepresentative();
        } else if (LetterType.JOINT_PARTY.equals(letterType)) {
            return reasonableAdjustmentsLetters.getJointParty();
        } else if (LetterType.OTHER_PARTY.equals(letterType)) {
            return reasonableAdjustmentsLetters.getOtherParty();
        }
        return null;
    }
}
