package uk.gov.hmcts.reform.sscs.service;

import static com.google.common.collect.Lists.newArrayList;
import static uk.gov.hmcts.reform.sscs.domain.email.EmailAttachment.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.pdf.service.client.PDFServiceClient;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.ccd.exception.CcdException;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.domain.email.SubmitYourAppealEmailTemplate;
import uk.gov.hmcts.reform.sscs.domain.pdf.PdfWrapper;
import uk.gov.hmcts.reform.sscs.exception.PdfGenerationException;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@Service
@Slf4j
public class SscsPdfService {

    private String appellantTemplatePath;
    private PDFServiceClient pdfServiceClient;
    private EmailService emailService;
    private PdfStoreService pdfStoreService;
    private SubmitYourAppealEmailTemplate submitYourAppealEmailTemplate;
    private CcdService ccdService;

    @Autowired
    public SscsPdfService(@Value("${appellant.appeal.html.template.path}") String appellantTemplatePath,
                          PDFServiceClient pdfServiceClient,
                          EmailService emailService,
                          PdfStoreService pdfStoreService,
                          SubmitYourAppealEmailTemplate submitYourAppealEmailTemplate,
                          CcdService ccdService) {
        this.appellantTemplatePath = appellantTemplatePath;
        this.pdfServiceClient = pdfServiceClient;
        this.emailService = emailService;
        this.pdfStoreService = pdfStoreService;
        this.submitYourAppealEmailTemplate = submitYourAppealEmailTemplate;
        this.ccdService = ccdService;
    }

    public byte[] generateAndSendPdf(SscsCaseData sscsCaseData, Long caseDetailsId, IdamTokens idamTokens) {
        byte[] pdf = generatePdf(sscsCaseData, caseDetailsId);

        sendPdfByEmail(sscsCaseData.getAppeal(), pdf);

        prepareCcdCaseForPdf(caseDetailsId, sscsCaseData, pdf, idamTokens);

        return pdf;
    }

    private byte[] generatePdf(SscsCaseData sscsCaseData, Long caseDetailsId) {
        byte[] template;
        try {
            template = getTemplate();
        } catch (IOException e) {
            throw new PdfGenerationException("Error getting template", e);
        }

        PdfWrapper pdfWrapper = PdfWrapper.builder().sscsCaseData(sscsCaseData).ccdCaseId(caseDetailsId)
                .isSignLanguageInterpreterRequired(sscsCaseData.getAppeal().getHearingOptions().wantsSignLanguageInterpreter())
                .isHearingLoopRequired(sscsCaseData.getAppeal().getHearingOptions().wantsHearingLoop())
                .isAccessibleHearingRoomRequired(sscsCaseData.getAppeal().getHearingOptions().wantsAccessibleHearingRoom())
                .currentDate(LocalDate.now())
                .repFullName(getRepFullName(sscsCaseData.getAppeal().getRep())).build();

        Map<String, Object> placeholders = Collections.singletonMap("PdfWrapper", pdfWrapper);
        return pdfServiceClient.generateFromHtml(template, placeholders);
    }

    private static String getRepFullName(Representative representative) {
        if (representative != null && representative.getName() != null) {
            return representative.getName().getFullName();
        } else {
            return null;
        }
    }

    private void prepareCcdCaseForPdf(Long caseId, SscsCaseData caseData, byte[] pdf, IdamTokens idamTokens) {
        String fileName = emailService.generateUniqueEmailId(caseData.getAppeal().getAppellant()) + ".pdf";
        mergeDocIntoCcd(fileName, pdf, caseId, caseData, idamTokens);
    }

    public void mergeDocIntoCcd(String fileName, byte[] pdf, Long caseId, SscsCaseData caseData, IdamTokens idamTokens) {
        List<SscsDocument> pdfDocuments = pdfStoreService.store(pdf, fileName);

        log.info("Appeal PDF stored in DM for Nino - {} and benefit type {}", caseData.getAppeal().getAppellant().getIdentity().getNino(),
                caseData.getAppeal().getBenefitType().getCode());

        if (caseId == null) {
            log.info("caseId is empty - skipping step to update CCD with PDF");
        } else {
            List<SscsDocument> allDocuments = combineEvidenceAndAppealPdf(caseData, pdfDocuments);
            SscsCaseData caseDataWithAppealPdf = caseData.toBuilder().sscsDocument(allDocuments).build();
            updateCaseInCcd(caseDataWithAppealPdf, caseId, "uploadDocument", idamTokens);
        }
    }

    private void sendPdfByEmail(Appeal appeal, byte[] pdf) {
        String appellantUniqueId = emailService.generateUniqueEmailId(appeal.getAppellant());
        emailService.sendEmail(submitYourAppealEmailTemplate.generateEmail(
                appellantUniqueId,
                newArrayList(pdf(pdf, appellantUniqueId + ".pdf")))
        );

        log.info("PDF email sent successfully for Nino - {} and benefit type {}", appeal.getAppellant().getIdentity().getNino(),
                appeal.getBenefitType().getCode());
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

    private byte[] getTemplate() throws IOException {
        InputStream in = getClass().getResourceAsStream(appellantTemplatePath);
        return IOUtils.toByteArray(in);
    }


}
