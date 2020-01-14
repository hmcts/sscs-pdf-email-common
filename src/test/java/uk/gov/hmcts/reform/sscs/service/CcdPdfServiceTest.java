package uk.gov.hmcts.reform.sscs.service;

import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.hmcts.reform.sscs.ccd.domain.EventType.UPLOAD_DOCUMENT;
import static uk.gov.hmcts.reform.sscs.ccd.util.CaseDataUtils.buildCaseData;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import uk.gov.hmcts.reform.sscs.ccd.domain.DocumentLink;
import uk.gov.hmcts.reform.sscs.ccd.domain.ScannedDocument;
import uk.gov.hmcts.reform.sscs.ccd.domain.ScannedDocumentDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocument;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocumentDetails;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@RunWith(JUnitParamsRunner.class)
public class CcdPdfServiceTest {

    @InjectMocks
    private CcdPdfService service;

    @Mock
    PdfStoreService pdfStoreService;

    @Mock
    CcdService ccdService;

    private SscsCaseData caseData = buildCaseData();

    @Before
    public void setup() {
        initMocks(this);
    }

    @Test
    public void mergeValidPdfAndStoreInDocumentStore() {
        List<SscsDocument> sscsDocuments = new ArrayList<>();
        sscsDocuments.add(SscsDocument.builder()
            .value(SscsDocumentDetails.builder()
                .documentFileName("Test.jpg")
                .build())
            .build());

        when(pdfStoreService.store(any(), any(), eq("dl6"))).thenReturn(sscsDocuments);
        when(ccdService.updateCase(any(), any(), any(), any(), any(), any()))
            .thenReturn(SscsCaseDetails.builder().data(caseData).build());

        byte[] pdf = {};
        service.mergeDocIntoCcd("Myfile.pdf", pdf, 1L, caseData, IdamTokens.builder().build(),
            "dl6");

        verify(pdfStoreService).store(any(), any(), eq("dl6"));
        verify(ccdService).updateCase(any(), any(), any(), eq("SSCS - upload document event"),
            eq("Uploaded document into SSCS"), any());
        assertEquals("Test.jpg", caseData.getSscsDocument().get(0).getValue().getDocumentFileName());
    }

    @Test
    @Parameters(method = "generateScenariosForSscsDocuments")
    public void givenAppellantStatement_shouldMergeDocIntoCcd(
        String fileName,
        List<SscsDocument> newStoredSscsDocuments,
        List<SscsDocument> existingSscsDocuments,
        List<ScannedDocument> existingScannedDocuments,
        int expectedNumberOfScannedDocs) {

        when(pdfStoreService.store(any(), any(), anyString())).thenReturn(newStoredSscsDocuments);
        when(ccdService.updateCase(any(), any(), any(), any(), any(), any()))
            .thenReturn(SscsCaseDetails.builder().data(caseData).build());

        caseData.setSscsDocument(existingSscsDocuments);
        caseData.setScannedDocuments(existingScannedDocuments);

        service.mergeDocIntoCcd(fileName, new byte[0], 1L, caseData, IdamTokens.builder().build(),
            "Other evidence");

        verify(pdfStoreService, times(1)).store(any(), eq(fileName),
            eq("Other evidence"));

        ArgumentCaptor<SscsCaseData> caseDataCaptor = ArgumentCaptor.forClass(SscsCaseData.class);
        verify(ccdService, times(1))
            .updateCase(caseDataCaptor.capture(), eq(1L), eq(UPLOAD_DOCUMENT.getCcdType()), anyString(),
                anyString(), any(IdamTokens.class));

        assertThat(caseDataCaptor.getValue().getScannedDocuments().size(), is(expectedNumberOfScannedDocs));
        if (!newStoredSscsDocuments.isEmpty()) {
            String expectedFilename = newStoredSscsDocuments.get(0).getValue().getDocumentFileName();
            Optional<ScannedDocument> scannedDocument = caseDataCaptor.getValue().getScannedDocuments().stream()
                .filter(scannedDoc -> expectedFilename.equals(scannedDoc.getValue().getFileName()))
                .findFirst();
            scannedDocument.ifPresent(document ->
                assertThat(document, is(buildExpectedScannedDocument(newStoredSscsDocuments))));
        }
    }

    private ScannedDocument buildExpectedScannedDocument(List<SscsDocument> newStoredSscsDocuments) {
        SscsDocumentDetails expectedDocValues = newStoredSscsDocuments.get(0).getValue();
        return ScannedDocument.builder()
            .value(ScannedDocumentDetails.builder()
                .fileName(expectedDocValues.getDocumentFileName())
                .url(expectedDocValues.getDocumentLink())
                .scannedDate(expectedDocValues.getDocumentDateAdded())
                .type("other")
                .build())
            .build();
    }

    private Object[] generateScenariosForSscsDocuments() {
        String doc1FileName = "Appellant statement 1 - SC0011111.pdf";
        SscsDocumentDetails sscsDocumentDetails1 = SscsDocumentDetails.builder()
            .documentFileName(doc1FileName)
            .documentDateAdded(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE))
            .documentLink(DocumentLink.builder().documentUrl("http://dm-store").build())
            .documentType("Other evidence")
            .build();

        String doc2FileName = "Appellant statement 2 - SC0022222.pdf";
        SscsDocumentDetails sscsDocumentDetails2 = SscsDocumentDetails.builder()
            .documentFileName(doc2FileName)
            .documentDateAdded(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE))
            .documentLink(DocumentLink.builder().documentUrl("http://dm-store2").build())
            .documentType("Other evidence")
            .build();

        List<SscsDocument> newStoredSscsDocumentsWithDoc1 = singletonList(SscsDocument.builder()
            .value(sscsDocumentDetails1)
            .build());

        List<SscsDocument> newStoredSscsDocumentsWithDoc2 = singletonList(SscsDocument.builder()
            .value(sscsDocumentDetails2)
            .build());

        int expectedNumberOfScannedDocsIsOne = 1;
        int expectedNumberOfScannedDocsIsZero = 0;
        int expectedNumberOfScannedDocsIsTwo = 2;

        ScannedDocumentDetails existingScannedDoc1 = ScannedDocumentDetails.builder()
            .fileName(doc1FileName)
            .url(DocumentLink.builder().documentUrl("http://dm-store").build())
            .type("other")
            .build();
        List<ScannedDocument> existingScannedDocsWithScannedDoc1 = singletonList(ScannedDocument.builder()
            .value(existingScannedDoc1)
            .build());


        return new Object[]{
            new Object[]{doc1FileName, newStoredSscsDocumentsWithDoc1, null, null, expectedNumberOfScannedDocsIsOne},
            new Object[]{doc1FileName, Collections.emptyList(), null, null, expectedNumberOfScannedDocsIsZero},
            new Object[]{doc2FileName, newStoredSscsDocumentsWithDoc2, null, existingScannedDocsWithScannedDoc1, expectedNumberOfScannedDocsIsTwo},
            new Object[]{doc2FileName, newStoredSscsDocumentsWithDoc2, newStoredSscsDocumentsWithDoc1, existingScannedDocsWithScannedDoc1, expectedNumberOfScannedDocsIsTwo}
        };
    }

    @Test
    public void mergeValidPdfAndStoreInDocumentStoreWithDescription() {
        byte[] pdf = {};
        when(ccdService.updateCase(any(), any(), any(), any(), any(), any())).thenReturn(SscsCaseDetails.builder().data(caseData).build());

        service.mergeDocIntoCcd("Myfile.pdf", pdf, 1L, caseData, IdamTokens.builder().build(), "My description", "dl6");

        verify(pdfStoreService).store(any(), any(), eq("dl6"));
        verify(ccdService).updateCase(any(), any(), any(), eq("SSCS - upload document event"), eq("My description"), any());
    }
}
