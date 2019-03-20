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

    public void mergeDocIntoCcd(String fileName, byte[] pdf, Long caseId, SscsCaseData caseData, IdamTokens idamTokens) {
        List<SscsDocument> pdfDocuments = pdfStoreService.store(pdf, fileName);

        log.info("Case {} PDF stored in DM for Nino - {} and benefit type {}", caseId, caseData.getAppeal().getAppellant().getIdentity().getNino(),
                caseData.getAppeal().getBenefitType().getCode());

        if (caseId == null) {
            log.info("caseId is empty - skipping step to update CCD with PDF");
        } else {
            List<SscsDocument> allDocuments = combineEvidenceAndAppealPdf(caseData, pdfDocuments);
            SscsCaseData caseDataWithAppealPdf = caseData.toBuilder().sscsDocument(allDocuments).build();
            updateCaseInCcd(caseDataWithAppealPdf, caseId, "uploadDocument", idamTokens);
        }
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

    private SscsCaseDetails updateCaseInCcd(SscsCaseData caseData, Long caseId, String eventId, IdamTokens idamTokens) {
        try {
            return ccdService.updateCase(caseData, caseId, eventId, "SSCS - appeal updated event", "Updated SSCS", idamTokens);
        } catch (CcdException ccdEx) {
            log.error("Failed to update ccd case but carrying on [" + caseId + "] ["
                    + caseData.getCaseReference() + "] with event [" + eventId + "]", ccdEx);
            return SscsCaseDetails.builder().build();
        }
    }

}
