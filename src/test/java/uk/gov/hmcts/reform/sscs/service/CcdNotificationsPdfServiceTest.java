package uk.gov.hmcts.reform.sscs.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static uk.gov.hmcts.reform.sscs.ccd.util.CaseDataUtils.buildCaseData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import uk.gov.hmcts.reform.pdf.service.client.PDFServiceClient;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.ccd.exception.CcdException;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.ccd.service.UpdateCcdCaseService;
import uk.gov.hmcts.reform.sscs.docmosis.domain.Pdf;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.model.LetterType;

@RunWith(JUnitParamsRunner.class)
public class CcdNotificationsPdfServiceTest {

    @InjectMocks
    private CcdNotificationsPdfService service;

    @Mock
    PdfStoreService pdfStoreService;

    @Mock
    CcdService ccdService;
    
    @Mock
    UpdateCcdCaseService updateCcdCaseService;

    @Mock
    PDFServiceClient pdfServiceClient;

    @Mock
    IdamService idamService;

    SscsCaseData caseData = buildCaseData().toBuilder().ccdCaseId("123").build();
    private List<SscsDocument> sscsDocuments;

    @Captor
    private ArgumentCaptor<SscsCaseData> caseDataCaptor;

    @Before
    public void setup() {
        openMocks(this);
        sscsDocuments = new ArrayList<>();
        sscsDocuments.add(SscsDocument.builder().value(SscsDocumentDetails.builder()
                .documentFileName("Test.jpg")
                .documentLink(DocumentLink.builder()
                        .documentUrl("aUrl")
                        .documentBinaryUrl("aUrl/binary")
                        .build())
                .build())
                .build());

        when(pdfStoreService.store(any(), any(), eq("dl6"))).thenReturn(sscsDocuments);
        when(idamService.getIdamTokens()).thenReturn(IdamTokens.builder().build());
        when(updateCcdCaseService.updateCaseWithoutRetry(any(), any(), any(), any(), any(), any())).thenReturn(SscsCaseDetails.builder().data(caseData).build());
        when(pdfServiceClient.generateFromHtml(any(), any())).thenReturn("bytes".getBytes());
    }

    @Test
    public void mergeCorrespondenceIntoCcd() {

        Correspondence correspondence = Correspondence.builder().value(
                CorrespondenceDetails.builder()
                        .sentOn("22 Jan 2021 11:00")
                        .from("from")
                        .to("to")
                        .body("the body")
                        .subject("a subject")
                        .eventType("event")
                        .correspondenceType(CorrespondenceType.Email)
                        .build()).build();

        service.mergeCorrespondenceIntoCcd(caseData, correspondence);
        verify(pdfServiceClient).generateFromHtml(any(), any());
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:00.pdf"), eq(CorrespondenceType.Email.name()));
        verify(updateCcdCaseService).updateCaseWithoutRetry(any(), any(), any(), eq("Notification sent"), eq("Notification sent via Gov Notify"), any());
    }

    @Test
    public void mergeLetterCorrespondenceIntoCcd() {
        byte[] bytes = "String".getBytes();
        Long caseId = Long.valueOf(caseData.getCcdCaseId());
        Correspondence correspondence = Correspondence.builder().value(
                CorrespondenceDetails.builder()
                        .sentOn("22 Jan 2021 11:00")
                        .from("from")
                        .to("to")
                        .subject("a subject")
                        .eventType("event")
                        .correspondenceType(CorrespondenceType.Email)
                        .build()).build();


        when(ccdService.getByCaseId(eq(caseId), eq(IdamTokens.builder().build()))).thenReturn(SscsCaseDetails.builder().data(caseData).build());
        service.mergeLetterCorrespondenceIntoCcd(bytes, caseId, correspondence);
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:00.pdf"), eq(CorrespondenceType.Email.name()));
        verify(updateCcdCaseService).updateCaseWithoutRetry(any(), any(), any(), eq("Notification sent"), eq("Notification sent via Gov Notify"), any());
    }

    @Test
    public void shouldMergeLetterCorrespondenceIntoCcdV2() {
        byte[] bytes = "String".getBytes();
        Long caseId = Long.valueOf(caseData.getCcdCaseId());
        Correspondence correspondence = Correspondence.builder().value(
                CorrespondenceDetails.builder()
                        .sentOn("22 Jan 2021 11:00")
                        .from("from")
                        .to("to")
                        .subject("a subject")
                        .eventType("event")
                        .correspondenceType(CorrespondenceType.Email)
                        .build()).build();


        service.mergeLetterCorrespondenceIntoCcdV2(bytes, caseId, correspondence);
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:00.pdf"), eq(CorrespondenceType.Email.name()));
        verify(updateCcdCaseService).updateCaseV2(
                eq(caseId), eq(EventType.NOTIFICATION_SENT.getCcdType()), eq("Notification sent"), eq("Notification sent via Gov Notify"), any(), any(Consumer.class));
    }

    @Test
    public void shouldNotThrowExceptionWhenUpdateCaseV2FailsForMergeLetterCorrespondenceIntoCcdV2() {
        byte[] bytes = "String".getBytes();
        Long caseId = Long.valueOf(caseData.getCcdCaseId());
        Correspondence correspondence = Correspondence.builder().value(
                CorrespondenceDetails.builder()
                        .sentOn("22 Jan 2021 11:00")
                        .from("from")
                        .to("to")
                        .subject("a subject")
                        .eventType("event")
                        .correspondenceType(CorrespondenceType.Email)
                        .build()).build();

        doThrow(new CcdException("some error when updating case"))
                .when(updateCcdCaseService).updateCaseV2(eq(caseId), eq(EventType.NOTIFICATION_SENT.getCcdType()), eq("Notification sent"), eq("Notification sent via Gov Notify"), any(), any(Consumer.class));

        service.mergeLetterCorrespondenceIntoCcdV2(bytes, caseId, correspondence);
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:00.pdf"), eq(CorrespondenceType.Email.name()));
        verify(updateCcdCaseService).updateCaseV2(
                eq(caseId), eq(EventType.NOTIFICATION_SENT.getCcdType()), eq("Notification sent"), eq("Notification sent via Gov Notify"), any(), any(Consumer.class));
    }

    @Test
    @Parameters({"APPELLANT", "REPRESENTATIVE", "APPOINTEE", "JOINT_PARTY", "OTHER_PARTY"})
    public void givenAReasonableAdjustmentPdfForALetterType_thenCreateReasonableAdjustmentsCorrespondenceIntoCcdForRelevantParty(LetterType letterType) {
        byte[] bytes = "String".getBytes();
        Pdf pdf = new Pdf(bytes, "adocument");
        List<Pdf> pdfs = Collections.singletonList(pdf);
        Long caseId = Long.valueOf(caseData.getCcdCaseId());
        DocumentLink documentLink = DocumentLink.builder().documentUrl("Http://document").documentFilename("evidence-document.pdf").build();
        Correspondence correspondence = Correspondence.builder().value(
                CorrespondenceDetails.builder()
                        .sentOn("22 Jan 2021 11:33")
                        .from("from")
                        .to("to")
                        .subject("a subject")
                        .eventType("event")
                        .documentLink(documentLink)
                        .correspondenceType(CorrespondenceType.Letter)
                        .reasonableAdjustmentStatus(ReasonableAdjustmentStatus.REQUIRED)
                        .build()).build();

        when(pdfStoreService.store(any(), any(), eq(CorrespondenceType.Letter.name()))).thenReturn(sscsDocuments);
        when(ccdService.getByCaseId(eq(caseId), eq(IdamTokens.builder().build()))).thenReturn(SscsCaseDetails.builder().data(caseData).build());
        service.mergeReasonableAdjustmentsCorrespondenceIntoCcd(pdfs, caseId, correspondence, letterType);
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:33.pdf"), eq(CorrespondenceType.Letter.name()));
        verify(updateCcdCaseService).updateCaseWithoutRetry(caseDataCaptor.capture(), any(), any(), eq("Notification sent"), eq("Stopped for reasonable adjustment to be sent"), any());

        Correspondence result = findLettersToCaptureByParty(caseDataCaptor.getValue().getReasonableAdjustmentsLetters(), letterType).get(0);
        assertEquals(sscsDocuments.get(0).getValue().getDocumentLink(), result.getValue().getDocumentLink());
        assertEquals(ReasonableAdjustmentStatus.REQUIRED, result.getValue().getReasonableAdjustmentStatus());
    }

    @Test
    @Parameters({"APPELLANT", "REPRESENTATIVE", "APPOINTEE", "JOINT_PARTY", "OTHER_PARTY"})
    public void shouldCreateReasonableAdjustmentsCorrespondenceIntoCcdV2ForLetterType(LetterType letterType) {
        byte[] bytes = "String".getBytes();
        Pdf pdf = new Pdf(bytes, "adocument");
        List<Pdf> pdfs = Collections.singletonList(pdf);
        Long caseId = Long.valueOf(caseData.getCcdCaseId());
        DocumentLink documentLink = DocumentLink.builder().documentUrl("Http://document").documentFilename("evidence-document.pdf").build();
        Correspondence correspondence = Correspondence.builder().value(
                CorrespondenceDetails.builder()
                        .sentOn("22 Jan 2021 11:33")
                        .from("from")
                        .to("to")
                        .subject("a subject")
                        .eventType("event")
                        .documentLink(documentLink)
                        .correspondenceType(CorrespondenceType.Letter)
                        .reasonableAdjustmentStatus(ReasonableAdjustmentStatus.REQUIRED)
                        .build()).build();

        when(pdfStoreService.store(any(), any(), eq(CorrespondenceType.Letter.name()))).thenReturn(sscsDocuments);
        service.mergeReasonableAdjustmentsCorrespondenceIntoCcdV2(pdfs, caseId, correspondence, letterType);
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:33.pdf"), eq(CorrespondenceType.Letter.name()));
        verify(updateCcdCaseService).updateCaseV2(
                eq(caseId),
                eq(EventType.STOP_BULK_PRINT_FOR_REASONABLE_ADJUSTMENT.getCcdType()),
                eq("Stop bulk print"),
                eq("Stopped for reasonable adjustment to be sent"),
                any(),
                any(Consumer.class));
    }

    @Test
    public void givenAReasonableAdjustmentPdfForALetterTypeWithExistingReasonableAdjustments_thenAppendReasonableAdjustmentsCorrespondenceIntoCcd() {
        DocumentLink documentLink = DocumentLink.builder().documentUrl("Http://document").documentFilename("evidence-document.pdf").build();

        when(pdfStoreService.store(any(), any(), eq(CorrespondenceType.Letter.name()))).thenReturn(sscsDocuments);
        List<Correspondence> existingCorrespondence = new ArrayList<>();
        existingCorrespondence.add(Correspondence.builder().value(CorrespondenceDetails.builder().sentOn("22 Oct 2020 11:33").documentLink(DocumentLink.builder().documentUrl("Testurl").build()).build()).build());
        caseData.setReasonableAdjustmentsLetters(ReasonableAdjustmentsLetters.builder().appellant(existingCorrespondence).build());

        Long caseId = Long.valueOf(caseData.getCcdCaseId());
        when(ccdService.getByCaseId(eq(caseId), eq(IdamTokens.builder().build()))).thenReturn(SscsCaseDetails.builder().data(caseData).build());
        byte[] bytes = "String".getBytes();
        Pdf pdf = new Pdf(bytes, "adocument");
        List<Pdf> pdfs = Collections.singletonList(pdf);
        Correspondence correspondence = Correspondence.builder().value(
                CorrespondenceDetails.builder()
                        .sentOn("22 Jan 2021 11:33")
                        .from("from")
                        .to("to")
                        .subject("a subject")
                        .eventType("event")
                        .documentLink(documentLink)
                        .correspondenceType(CorrespondenceType.Letter)
                        .reasonableAdjustmentStatus(ReasonableAdjustmentStatus.REQUIRED)
                        .build()).build();

        service.mergeReasonableAdjustmentsCorrespondenceIntoCcd(pdfs, caseId, correspondence, LetterType.APPELLANT);
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:33.pdf"), eq(CorrespondenceType.Letter.name()));
        verify(updateCcdCaseService).updateCaseWithoutRetry(caseDataCaptor.capture(), any(), any(), eq("Notification sent"), eq("Stopped for reasonable adjustment to be sent"), any());

        assertEquals(sscsDocuments.get(0).getValue().getDocumentLink(), caseDataCaptor.getValue().getReasonableAdjustmentsLetters().getAppellant().get(0).getValue().getDocumentLink());
        assertEquals(ReasonableAdjustmentStatus.REQUIRED, caseDataCaptor.getValue().getReasonableAdjustmentsLetters().getAppellant().get(0).getValue().getReasonableAdjustmentStatus());
        assertEquals("Testurl", caseDataCaptor.getValue().getReasonableAdjustmentsLetters().getAppellant().get(1).getValue().getDocumentLink().getDocumentUrl());
    }

    @Test
    @Parameters({"APPELLANT", "REPRESENTATIVE", "APPOINTEE", "JOINT_PARTY", "OTHER_PARTY"})
    public void givenAReasonableAdjustmentBytesForALetterType_thenCreateReasonableAdjustmentsCorrespondenceIntoCcdForRelevantParty(LetterType letterType) {
        byte[] bytes = "String".getBytes();
        Long caseId = Long.valueOf(caseData.getCcdCaseId());
        DocumentLink documentLink = DocumentLink.builder().documentUrl("Http://document").documentFilename("evidence-document.pdf").build();
        Correspondence correspondence = Correspondence.builder().value(
                CorrespondenceDetails.builder()
                        .sentOn("22 Jan 2021 11:33")
                        .from("from")
                        .to("to")
                        .subject("a subject")
                        .eventType("event")
                        .documentLink(documentLink)
                        .correspondenceType(CorrespondenceType.Letter)
                        .reasonableAdjustmentStatus(ReasonableAdjustmentStatus.REQUIRED)
                        .build()).build();

        when(pdfStoreService.store(any(), any(), eq(CorrespondenceType.Letter.name()))).thenReturn(sscsDocuments);
        when(ccdService.getByCaseId(eq(caseId), eq(IdamTokens.builder().build()))).thenReturn(SscsCaseDetails.builder().data(caseData).build());
        service.mergeReasonableAdjustmentsCorrespondenceIntoCcd(bytes, caseId, correspondence, letterType);
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:33.pdf"), eq(CorrespondenceType.Letter.name()));
        verify(updateCcdCaseService).updateCaseWithoutRetry(caseDataCaptor.capture(), any(), any(), eq("Notification sent"), eq("Stopped for reasonable adjustment to be sent"), any());

        Correspondence result = findLettersToCaptureByParty(caseDataCaptor.getValue().getReasonableAdjustmentsLetters(), letterType).get(0);
        assertEquals(sscsDocuments.get(0).getValue().getDocumentLink(), result.getValue().getDocumentLink());
        assertEquals(ReasonableAdjustmentStatus.REQUIRED, result.getValue().getReasonableAdjustmentStatus());
    }

    @Test
    @Parameters({"APPELLANT", "REPRESENTATIVE", "APPOINTEE", "JOINT_PARTY", "OTHER_PARTY"})
    public void ShouldMergeReasonableAdjustmentsCorrespondenceIntoCcdV2ForLetterTypeAndRelevantParties(LetterType letterType) {
        byte[] bytes = "String".getBytes();
        Long caseId = Long.valueOf(caseData.getCcdCaseId());
        DocumentLink documentLink = DocumentLink.builder().documentUrl("Http://document").documentFilename("evidence-document.pdf").build();
        Correspondence correspondence = Correspondence.builder().value(
                CorrespondenceDetails.builder()
                        .sentOn("22 Jan 2021 11:33")
                        .from("from")
                        .to("to")
                        .subject("a subject")
                        .eventType("event")
                        .documentLink(documentLink)
                        .correspondenceType(CorrespondenceType.Letter)
                        .reasonableAdjustmentStatus(ReasonableAdjustmentStatus.REQUIRED)
                        .build()).build();

        when(pdfStoreService.store(any(), any(), eq(CorrespondenceType.Letter.name()))).thenReturn(sscsDocuments);
        service.mergeReasonableAdjustmentsCorrespondenceIntoCcdV2(bytes, caseId, correspondence, letterType);
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:33.pdf"), eq(CorrespondenceType.Letter.name()));
        verify(updateCcdCaseService).updateCaseV2(
                eq(caseId),
                eq(EventType.STOP_BULK_PRINT_FOR_REASONABLE_ADJUSTMENT.getCcdType()),
                eq("Stop bulk print"),
                eq("Stopped for reasonable adjustment to be sent"),
                any(),
                any(Consumer.class));
    }

    @Test
    @Parameters({"APPELLANT", "REPRESENTATIVE", "APPOINTEE", "JOINT_PARTY", "OTHER_PARTY"})
    public void shouldNotThrowExceptionWhenCaseUpdateFailsForMergeReasonableAdjustmentsCorrespondenceIntoCcdV2(LetterType letterType) {
        byte[] bytes = "String".getBytes();
        Long caseId = Long.valueOf(caseData.getCcdCaseId());
        DocumentLink documentLink = DocumentLink.builder().documentUrl("Http://document").documentFilename("evidence-document.pdf").build();
        Correspondence correspondence = Correspondence.builder().value(
                CorrespondenceDetails.builder()
                        .sentOn("22 Jan 2021 11:33")
                        .from("from")
                        .to("to")
                        .subject("a subject")
                        .eventType("event")
                        .documentLink(documentLink)
                        .correspondenceType(CorrespondenceType.Letter)
                        .reasonableAdjustmentStatus(ReasonableAdjustmentStatus.REQUIRED)
                        .build()).build();

        doThrow(new CcdException("some error when updating case")).when(updateCcdCaseService).updateCaseV2(
                eq(caseId),
                eq(EventType.STOP_BULK_PRINT_FOR_REASONABLE_ADJUSTMENT.getCcdType()),
                eq("Stop bulk print"),
                eq("Stopped for reasonable adjustment to be sent"),
                any(),
                any(Consumer.class)
        );

        when(pdfStoreService.store(any(), any(), eq(CorrespondenceType.Letter.name()))).thenReturn(sscsDocuments);
        service.mergeReasonableAdjustmentsCorrespondenceIntoCcdV2(bytes, caseId, correspondence, letterType);
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:33.pdf"), eq(CorrespondenceType.Letter.name()));
        verify(updateCcdCaseService).updateCaseV2(
                eq(caseId),
                eq(EventType.STOP_BULK_PRINT_FOR_REASONABLE_ADJUSTMENT.getCcdType()),
                eq("Stop bulk print"),
                eq("Stopped for reasonable adjustment to be sent"),
                any(),
                any(Consumer.class));
    }

    @Test
    public void givenAReasonableAdjustmentBytesForALetterTypeWithExistingReasonableAdjustments_thenAppendReasonableAdjustmentsCorrespondenceIntoCcd() {
        DocumentLink documentLink = DocumentLink.builder().documentUrl("Http://document").documentFilename("evidence-document.pdf").build();

        when(pdfStoreService.store(any(), any(), eq(CorrespondenceType.Letter.name()))).thenReturn(sscsDocuments);
        List<Correspondence> existingCorrespondence = new ArrayList<>();
        existingCorrespondence.add(Correspondence.builder().value(CorrespondenceDetails.builder().sentOn("22 Oct 2020 11:33").documentLink(DocumentLink.builder().documentUrl("Testurl").build()).build()).build());
        caseData.setReasonableAdjustmentsLetters(ReasonableAdjustmentsLetters.builder().appellant(existingCorrespondence).build());

        Long caseId = Long.valueOf(caseData.getCcdCaseId());
        when(ccdService.getByCaseId(eq(caseId), eq(IdamTokens.builder().build()))).thenReturn(SscsCaseDetails.builder().data(caseData).build());
        byte[] bytes = "String".getBytes();
        Correspondence correspondence = Correspondence.builder().value(
                CorrespondenceDetails.builder()
                        .sentOn("22 Jan 2021 11:33")
                        .from("from")
                        .to("to")
                        .subject("a subject")
                        .eventType("event")
                        .documentLink(documentLink)
                        .correspondenceType(CorrespondenceType.Letter)
                        .reasonableAdjustmentStatus(ReasonableAdjustmentStatus.REQUIRED)
                        .build()).build();

        service.mergeReasonableAdjustmentsCorrespondenceIntoCcd(bytes, caseId, correspondence, LetterType.APPELLANT);
        verify(pdfStoreService).store(any(), eq("event 22 Jan 2021 11:33.pdf"), eq(CorrespondenceType.Letter.name()));
        verify(updateCcdCaseService).updateCaseWithoutRetry(caseDataCaptor.capture(), any(), any(), eq("Notification sent"), eq("Stopped for reasonable adjustment to be sent"), any());

        assertEquals(sscsDocuments.get(0).getValue().getDocumentLink(), caseDataCaptor.getValue().getReasonableAdjustmentsLetters().getAppellant().get(0).getValue().getDocumentLink());
        assertEquals(ReasonableAdjustmentStatus.REQUIRED, caseDataCaptor.getValue().getReasonableAdjustmentsLetters().getAppellant().get(0).getValue().getReasonableAdjustmentStatus());
        assertEquals("Testurl", caseDataCaptor.getValue().getReasonableAdjustmentsLetters().getAppellant().get(1).getValue().getDocumentLink().getDocumentUrl());
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
