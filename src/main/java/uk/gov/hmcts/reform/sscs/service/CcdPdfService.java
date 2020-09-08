package uk.gov.hmcts.reform.sscs.service;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static uk.gov.hmcts.reform.sscs.ccd.domain.EventType.UPLOAD_DOCUMENT;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.sscs.ccd.domain.ScannedDocument;
import uk.gov.hmcts.reform.sscs.ccd.domain.ScannedDocumentDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocument;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocumentDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocumentTranslationStatus;
import uk.gov.hmcts.reform.sscs.ccd.exception.CcdException;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@Service
@Slf4j
public class CcdPdfService {

    private static final String APPELLANT_STATEMENT = "Appellant statement ";
    private static final String REPRESENTATIVE_STATEMENT = "Representative statement ";
    public static final String YES = "Yes";

    @Autowired
    private PdfStoreService pdfStoreService;

    @Autowired
    private CcdService ccdService;

    // can be removed once COR team decide what to pass into the documentType field
    public SscsCaseData mergeDocIntoCcd(String fileName, byte[] pdf, Long caseId, SscsCaseData caseData,
                                        IdamTokens idamTokens) {
        return updateAndMerge(fileName, pdf, caseId, caseData, idamTokens, "Uploaded document into SSCS",
                null);
    }

    public SscsCaseData mergeDocIntoCcd(String fileName, byte[] pdf, Long caseId, SscsCaseData caseData,
                                        IdamTokens idamTokens, String documentType) {
        return updateAndMerge(fileName, pdf, caseId, caseData, idamTokens, "Uploaded document into SSCS",
                documentType);
    }

    public SscsCaseData mergeDocIntoCcd(String fileName, byte[] pdf, Long caseId, SscsCaseData caseData,
                                        IdamTokens idamTokens, String description, String documentType) {
        return updateAndMerge(fileName, pdf, caseId, caseData, idamTokens, description, documentType);
    }

    private SscsCaseData updateAndMerge(String fileName, byte[] pdf, Long caseId, SscsCaseData caseData,
                                        IdamTokens idamTokens, String description, String documentType) {
        updateDoc(fileName, pdf, caseId, caseData, documentType);
        return updateCaseInCcd(caseData, caseId, UPLOAD_DOCUMENT.getCcdType(), idamTokens, description).getData();
    }

    public SscsCaseData updateDoc(String fileName, byte[] pdf, Long caseId, SscsCaseData caseData, String documentType) {
        return this.updateDoc(fileName, pdf, caseId, caseData, documentType, null);
    }

    public SscsCaseData updateDoc(String fileName, byte[] pdf, Long caseId, SscsCaseData caseData, String documentType, SscsDocumentTranslationStatus documentTranslationStatus) {
        List<SscsDocument> pdfDocuments = pdfStoreService.store(pdf, fileName, documentType, documentTranslationStatus);

        if (!pdfDocuments.isEmpty()) {
            log.info("Case {} PDF stored in DM for benefit type {}", caseId,
                    caseData.getAppeal().getBenefitType().getCode());
        }

        if (caseId == null) {
            log.info("caseId is empty - skipping step to update CCD with PDF");
            return caseData;
        }
        updateCaseDataWithNewDoc(fileName, caseData, pdfDocuments);
        if (documentTranslationStatus != null && documentTranslationStatus.equals(SscsDocumentTranslationStatus.TRANSLATION_REQUIRED)) {
            caseData.setTranslationWorkOutstanding(YES);
        }
        return caseData;
    }

    private void updateCaseDataWithNewDoc(String fileName, SscsCaseData caseData, List<SscsDocument> pdfDocuments) {
        if (fileName.startsWith(APPELLANT_STATEMENT) || fileName.startsWith(REPRESENTATIVE_STATEMENT)) {
            caseData.setScannedDocuments(ListUtils.union(emptyIfNull(caseData.getScannedDocuments()),
                    buildScannedDocListFromSscsDoc(pdfDocuments)));
        } else {
            caseData.setSscsDocument(ListUtils.union(emptyIfNull(caseData.getSscsDocument()),
                    emptyIfNull(pdfDocuments)));
        }
    }

    private List<ScannedDocument> buildScannedDocListFromSscsDoc(List<SscsDocument> pdfDocuments) {
        if (pdfDocuments.isEmpty()) {
            return Collections.emptyList();
        }
        SscsDocumentDetails pdfDocDetails = pdfDocuments.get(0).getValue();

        String dateAdded = null;
        if (pdfDocDetails.getDocumentDateAdded() != null) {
            dateAdded = LocalDate.parse(pdfDocDetails.getDocumentDateAdded()).atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME);
        } else {
            dateAdded = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
        }

        ScannedDocument scannedDoc = ScannedDocument.builder()
                .value(ScannedDocumentDetails.builder()
                        .fileName(pdfDocDetails.getDocumentFileName())
                        .url(pdfDocDetails.getDocumentLink())
                        .scannedDate(dateAdded)
                        .type("other")
                        .build())
                .build();
        return Collections.singletonList(scannedDoc);
    }

    private SscsCaseDetails updateCaseInCcd(SscsCaseData caseData, Long caseId, String eventId, IdamTokens idamTokens,
                                            String description) {
        try {
            return ccdService.updateCase(caseData, caseId, eventId, "SSCS - upload document event",
                    description, idamTokens);
        } catch (CcdException ccdEx) {
            log.error("Failed to update ccd case but carrying on [" + caseId + "] ["
                    + caseData.getCaseReference() + "] with event [" + eventId + "]", ccdEx);
            return SscsCaseDetails.builder().build();
        }
    }

}
