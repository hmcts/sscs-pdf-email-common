package uk.gov.hmcts.reform.sscs.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.pdf.service.client.PDFServiceClient;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.ccd.exception.CcdException;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.exception.PdfGenerationException;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@Service
@Slf4j
public class CcdNotificationsPdfService {

    @Autowired
    private PdfStoreService pdfStoreService;

    @Autowired
    private PDFServiceClient pdfServiceClient;

    @Autowired
    private CcdService ccdService;

    @Autowired
    private IdamService idamService;


    public SscsCaseData mergeCorrespondenceIntoCcd(SscsCaseData sscsCaseData, Correspondence correspondence) {
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("body", correspondence.getValue().getBody());
        placeholders.put("subject", correspondence.getValue().getSubject());
        placeholders.put("sentOn", correspondence.getValue().getSentOn());
        placeholders.put("from", correspondence.getValue().getFrom());
        placeholders.put("to", correspondence.getValue().getTo());

        byte[] template;
        try {
            template = getSentEmailTemplate(sscsCaseData.isLanguagePreferenceWelsh());
        } catch (IOException e) {
            throw new PdfGenerationException("Error getting template", e);
        }

        byte[] pdf = pdfServiceClient.generateFromHtml(template, placeholders);
        String filename = String.format("%s %s.pdf", correspondence.getValue().getEventType(), correspondence.getValue().getSentOn());
        List<SscsDocument> pdfDocuments = pdfStoreService.store(pdf, filename, correspondence.getValue().getCorrespondenceType().name());
        final List<Correspondence> correspondences = pdfDocuments.stream().map(doc ->
                correspondence.toBuilder().value(correspondence.getValue().toBuilder()
                        .documentLink(doc.getValue().getDocumentLink())
                        .build()).build()
        ).collect(Collectors.toList());

        List<Correspondence> existingCorrespondence = sscsCaseData.getCorrespondence() == null ? new ArrayList<>() : sscsCaseData.getCorrespondence();
        List<Correspondence> allCorrespondence = new ArrayList<>(existingCorrespondence);
        allCorrespondence.addAll(correspondences);
        allCorrespondence.sort(Comparator.reverseOrder());
        sscsCaseData.setCorrespondence(allCorrespondence);

        IdamTokens idamTokens = idamService.getIdamTokens();
        SscsCaseDetails caseDetails = updateCaseInCcd(sscsCaseData, Long.parseLong(sscsCaseData.getCcdCaseId()), EventType.NOTIFICATION_SENT.getCcdType(), idamTokens);

        return caseDetails.getData();
    }

    public SscsCaseData mergeLetterCorrespondenceIntoCcd(byte[] pdf, Long ccdCaseId, Correspondence correspondence) {
        String filename = String.format("%s %s.pdf", correspondence.getValue().getEventType(), correspondence.getValue().getSentOn());
        List<SscsDocument> pdfDocuments = pdfStoreService.store(pdf, filename, correspondence.getValue().getCorrespondenceType().name());
        final List<Correspondence> correspondences = pdfDocuments.stream().map(doc ->
                correspondence.toBuilder().value(correspondence.getValue().toBuilder()
                        .documentLink(doc.getValue().getDocumentLink())
                        .build()).build()
        ).collect(Collectors.toList());

        IdamTokens idamTokens = idamService.getIdamTokens();
        final SscsCaseDetails sscsCaseDetails = ccdService.getByCaseId(ccdCaseId, idamTokens);
        final SscsCaseData sscsCaseData = sscsCaseDetails.getData();

        List<Correspondence> existingCorrespondence = sscsCaseData.getCorrespondence() == null ? new ArrayList<>() : sscsCaseData.getCorrespondence();
        List<Correspondence> allCorrespondence = new ArrayList<>(existingCorrespondence);
        allCorrespondence.addAll(correspondences);
        allCorrespondence.sort(Comparator.reverseOrder());
        sscsCaseData.setCorrespondence(allCorrespondence);

        SscsCaseDetails caseDetails = updateCaseInCcd(sscsCaseData, Long.parseLong(sscsCaseData.getCcdCaseId()), EventType.NOTIFICATION_SENT.getCcdType(), idamTokens);

        return caseDetails.getData();
    }


    private SscsCaseDetails updateCaseInCcd(SscsCaseData caseData, Long caseId, String eventId, IdamTokens idamTokens) {
        try {
            return ccdService.updateCase(caseData, caseId, eventId, "Notification sent", "Notification sent via Gov Notify", idamTokens);
        } catch (CcdException ccdEx) {
            log.error("Failed to update ccd case but carrying on [" + caseId + "] ["
                    + caseData.getCaseReference() + "] with event [" + eventId + "]", ccdEx);
            return SscsCaseDetails.builder().build();
        }
    }

    private byte[] getSentEmailTemplate(boolean isWelsh) throws IOException {
        InputStream in = getClass().getResourceAsStream(isWelsh ? "/templates/sent_welsh_notification.html"
                : "/templates/sent_notification.html");
        return IOUtils.toByteArray(in);
    }

}
