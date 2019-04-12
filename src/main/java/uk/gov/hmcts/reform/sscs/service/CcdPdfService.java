package uk.gov.hmcts.reform.sscs.service;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocument;
import uk.gov.hmcts.reform.sscs.ccd.exception.CcdException;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@Service
@Slf4j
public class CcdPdfService {

    @Autowired
    private PdfStoreService pdfStoreService;

    @Autowired
    private CcdService ccdService;

    public SscsCaseData mergeDocIntoCcd(String fileName, byte[] pdf, Long caseId, SscsCaseData caseData, IdamTokens idamTokens) {
        return updateAndMerge(fileName, pdf, caseId, caseData, idamTokens, "Uploaded document into SSCS");
    }

    public SscsCaseData mergeDocIntoCcd(String fileName, byte[] pdf, Long caseId, SscsCaseData caseData, IdamTokens idamTokens, String description) {
        return updateAndMerge(fileName, pdf, caseId, caseData, idamTokens, description);
    }

    private SscsCaseData updateAndMerge(String fileName, byte[] pdf, Long caseId, SscsCaseData caseData, IdamTokens idamTokens, String description) {
        List<SscsDocument> pdfDocuments = pdfStoreService.store(pdf, fileName);

        log.info("Case {} PDF stored in DM for benefit type {}", caseId,
                caseData.getAppeal().getBenefitType().getCode());

        if (caseId == null) {
            log.info("caseId is empty - skipping step to update CCD with PDF");
        } else {
            List<SscsDocument> allDocuments = combineEvidenceAndAppealPdf(caseData, pdfDocuments);
            SscsCaseData caseDataWithAppealPdf = caseData.toBuilder().sscsDocument(allDocuments).build();
            SscsCaseDetails caseDetails = updateCaseInCcd(caseDataWithAppealPdf, caseId, "uploadDocument", idamTokens, description);
            return caseDetails.getData();
        }
        return caseData;
    }

    private List<SscsDocument> combineEvidenceAndAppealPdf(SscsCaseData caseData, List<SscsDocument> pdfDocuments) {
        List<SscsDocument> evidenceDocuments = caseData.getSscsDocument();
        List<SscsDocument> allDocuments = new ArrayList<>();
        if (evidenceDocuments != null) {
            allDocuments.addAll(evidenceDocuments);
        }
        allDocuments.addAll(pdfDocuments);
        return allDocuments;
    }

    private SscsCaseDetails updateCaseInCcd(SscsCaseData caseData, Long caseId, String eventId, IdamTokens idamTokens, String description) {
        try {
            return ccdService.updateCase(caseData, caseId, eventId, "SSCS - upload document event", description, idamTokens);
        } catch (CcdException ccdEx) {
            log.error("Failed to update ccd case but carrying on [" + caseId + "] ["
                    + caseData.getCaseReference() + "] with event [" + eventId + "]", ccdEx);
            return SscsCaseDetails.builder().build();
        }
    }

}
