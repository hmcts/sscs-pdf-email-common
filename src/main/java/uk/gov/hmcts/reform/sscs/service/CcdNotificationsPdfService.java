package uk.gov.hmcts.reform.sscs.service;

import static uk.gov.hmcts.reform.sscs.model.LetterType.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.pdf.service.client.PDFServiceClient;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.ccd.exception.CcdException;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.ccd.service.UpdateCcdCaseService;
import uk.gov.hmcts.reform.sscs.docmosis.domain.Pdf;
import uk.gov.hmcts.reform.sscs.exception.PdfGenerationException;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.model.LetterType;

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
    private UpdateCcdCaseService updateCcdCaseService;

    @Autowired
    private IdamService idamService;

    private static final String DEFAULT_SENDER_TYPE = "Gov Notify";


    public SscsCaseData mergeCorrespondenceIntoCcd(SscsCaseData sscsCaseData, Correspondence correspondence) {
        List<Correspondence> existingCorrespondence = sscsCaseData.getCorrespondence() == null ? new ArrayList<>() : sscsCaseData.getCorrespondence();
        List<Correspondence> allCorrespondence = new ArrayList<>(existingCorrespondence);
        allCorrespondence.addAll(getCorrespondences(correspondence));
        allCorrespondence.sort(Comparator.reverseOrder());
        sscsCaseData.setCorrespondence(allCorrespondence);

        SscsCaseDetails caseDetails = updateCaseInCcd(sscsCaseData, Long.parseLong(sscsCaseData.getCcdCaseId()), EventType.NOTIFICATION_SENT.getCcdType(),
                idamService.getIdamTokens(), "Notification sent via Gov Notify");

        return caseDetails.getData();
    }

    /**
     * This method generates PDF using HTML, stores in doc store and updates SSCS case data retrieved from DB with correspondence.
     *
     * @param caseId         - CCD case id
     * @param correspondence - Correspondence which needs to be added to the case data
     */
    public void mergeCorrespondenceIntoCcdV2(Long caseId, Correspondence correspondence) {
        List<Correspondence> updatedCorrespondences = getCorrespondences(correspondence);

        Consumer<SscsCaseDetails> caseDataConsumer = caseDetails -> {
            SscsCaseData caseData = caseDetails.getData();
            List<Correspondence> existingCorrespondence = caseData.getCorrespondence() == null ? new ArrayList<>() : caseData.getCorrespondence();
            List<Correspondence> allCorrespondence = new ArrayList<>(existingCorrespondence);
            allCorrespondence.addAll(updatedCorrespondences);
            allCorrespondence.sort(Comparator.reverseOrder());
            caseData.setCorrespondence(allCorrespondence);
        };

        try {
            updateCcdCaseService.updateCaseV2(caseId, EventType.NOTIFICATION_SENT.getCcdType(), "Notification sent", "Notification sent via Gov Notify", idamService.getIdamTokens(), caseDataConsumer);
        } catch (CcdException ccdEx) {
            log.error("Failed to update ccd case using v2 but carrying on [" + caseId + "] ["
                    + caseId + "] with event [" + EventType.NOTIFICATION_SENT.getCcdType() + "]", ccdEx);
        }
    }

    public SscsCaseData mergeLetterCorrespondenceIntoCcd(byte[] pdf, Long ccdCaseId, Correspondence correspondence) {
        return mergeLetterCorrespondenceIntoCcd(pdf, ccdCaseId, correspondence, DEFAULT_SENDER_TYPE);
    }

    public SscsCaseData mergeLetterCorrespondenceIntoCcd(byte[] pdf, Long ccdCaseId, Correspondence correspondence, String senderType) {
        final List<Correspondence> correspondences = getCorrespondences(pdf, correspondence);

        IdamTokens idamTokens = idamService.getIdamTokens();
        final SscsCaseDetails sscsCaseDetails = ccdService.getByCaseId(ccdCaseId, idamTokens);
        final SscsCaseData sscsCaseData = sscsCaseDetails.getData();

        List<Correspondence> existingCorrespondence = sscsCaseData.getCorrespondence() == null ? new ArrayList<>() : sscsCaseData.getCorrespondence();
        List<Correspondence> allCorrespondence = new ArrayList<>(existingCorrespondence);
        allCorrespondence.addAll(correspondences);
        allCorrespondence.sort(Comparator.reverseOrder());
        sscsCaseData.setCorrespondence(allCorrespondence);

        String description = String.format("Notification sent via %s", senderType);
        SscsCaseDetails caseDetails = updateCaseInCcd(sscsCaseData, Long.parseLong(sscsCaseData.getCcdCaseId()), EventType.NOTIFICATION_SENT.getCcdType(),
                idamTokens, description);

        return caseDetails.getData();
    }

    public void mergeLetterCorrespondenceIntoCcdV2(byte[] pdf, Long ccdCaseId, Correspondence correspondence) {
        mergeLetterCorrespondenceIntoCcdV2(pdf, ccdCaseId, correspondence, DEFAULT_SENDER_TYPE);
    }

    public void mergeLetterCorrespondenceIntoCcdV2(byte[] pdf, Long ccdCaseId, Correspondence correspondence, String senderType) {
        final List<Correspondence> correspondences = getCorrespondences(pdf, correspondence);

        Consumer<SscsCaseDetails> caseDataConsumer = sscsCaseDetails -> {
            SscsCaseData sscsCaseData = sscsCaseDetails.getData();
            List<Correspondence> existingCorrespondence = sscsCaseData.getCorrespondence() == null ? new ArrayList<>() : sscsCaseData.getCorrespondence();
            List<Correspondence> allCorrespondence = new ArrayList<>(existingCorrespondence);
            allCorrespondence.addAll(correspondences);
            allCorrespondence.sort(Comparator.reverseOrder());
            sscsCaseData.setCorrespondence(allCorrespondence);
        };

        String description = String.format("Notification sent via %s", senderType);
        log.info("Updating ccd case using v2 for {} with event {}", ccdCaseId, EventType.NOTIFICATION_SENT.getCcdType());
        try {
            updateCcdCaseService.updateCaseV2(ccdCaseId,
                    EventType.NOTIFICATION_SENT.getCcdType(),
                    "Notification sent",
                    description,
                    idamService.getIdamTokens(),
                    caseDataConsumer);
        } catch (CcdException ccdEx) {
            log.error("Failed to update ccd case using v2 but carrying on [" + ccdCaseId + "] ["
                    + ccdCaseId + "] with event [" + EventType.NOTIFICATION_SENT.getCcdType() + "]", ccdEx);
        }
    }

    public SscsCaseData mergeReasonableAdjustmentsCorrespondenceIntoCcd(List<Pdf> pdfs, Long ccdCaseId, Correspondence correspondence, LetterType letterType) {
        PDFMergerUtility merger = new PDFMergerUtility();
        for (Pdf pdf : pdfs) {
            merger.addSource(new ByteArrayInputStream(pdf.getContent()));
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        merger.setDestinationStream(baos);
        try {
            merger.mergeDocuments(null);
        } catch (IOException e) {
            log.error("Failed to create pdf of letter for {}", ccdCaseId, e);
        }

        byte[] letterDocument = baos.toByteArray();

        return mergeReasonableAdjustmentsCorrespondenceIntoCcd(letterDocument, ccdCaseId, correspondence, letterType);
    }

    public SscsCaseData mergeReasonableAdjustmentsCorrespondenceIntoCcd(byte[] letterDocument, Long ccdCaseId, Correspondence correspondence, LetterType letterType) {
        IdamTokens idamTokens = idamService.getIdamTokens();

        var correspondences = getCorrespondences(letterDocument, correspondence);
        Consumer<SscsCaseDetails> caseDetailsConsumer = caseDetails -> {
            caseDetails.getData().setReasonableAdjustmentsLetters(buildCorrespondenceByParty(caseDetails.getData(), correspondences, letterType));
            caseDetails.getData().updateReasonableAdjustmentsOutstanding();
        };

        log.info("Creating a reasonable adjustment for {}", ccdCaseId);

        SscsCaseDetails caseDetails = updateCaseV2InCcd(caseDetailsConsumer, ccdCaseId, EventType.STOP_BULK_PRINT_FOR_REASONABLE_ADJUSTMENT.getCcdType(),
                idamTokens, "Stopped for reasonable adjustment to be sent");

        return caseDetails.getData();
    }

    public void mergeReasonableAdjustmentsCorrespondenceIntoCcdV2(List<Pdf> pdfs, Long ccdCaseId, Correspondence correspondence, LetterType letterType) {
        byte[] letterDocument = getMergedDocument(pdfs, ccdCaseId);

        mergeReasonableAdjustmentsCorrespondenceIntoCcdV2(letterDocument, ccdCaseId, correspondence, letterType);
    }

    public void mergeReasonableAdjustmentsCorrespondenceIntoCcdV2(byte[] letterDocument, Long ccdCaseId, Correspondence correspondence, LetterType letterType) {
        List<Correspondence> correspondenceList = getCorrespondences(letterDocument, correspondence);

        Consumer<SscsCaseDetails> caseDataConsumer = sscsCaseDetails -> {
            SscsCaseData sscsCaseData = sscsCaseDetails.getData();
            sscsCaseData.setReasonableAdjustmentsLetters(
                    buildCorrespondenceByParty(sscsCaseData, correspondenceList, letterType));
            sscsCaseData.updateReasonableAdjustmentsOutstanding();
        };

        log.info("Creating a reasonable adjustment using v2 for {} with event {}", ccdCaseId, EventType.STOP_BULK_PRINT_FOR_REASONABLE_ADJUSTMENT.getCcdType());
        try {
            updateCcdCaseService.updateCaseV2(
                    ccdCaseId,
                    EventType.STOP_BULK_PRINT_FOR_REASONABLE_ADJUSTMENT.getCcdType(),
                    "Stop bulk print",
                    "Stopped for reasonable adjustment to be sent",
                    idamService.getIdamTokens(),
                    caseDataConsumer);
        } catch (CcdException ccdEx) {
            log.error("Failed to update ccd case using v2 but carrying on [" + ccdCaseId + "] ["
                    + ccdCaseId + "] with event [" + EventType.NOTIFICATION_SENT.getCcdType() + "]", ccdEx);
        }
    }

    private ReasonableAdjustmentsLetters buildCorrespondenceByParty(SscsCaseData sscsCaseData, List<Correspondence> correspondences, LetterType letterType) {
        ReasonableAdjustmentsLetters reasonableAdjustmentsLetters = sscsCaseData.getReasonableAdjustmentsLetters() == null ? ReasonableAdjustmentsLetters.builder().build() : sscsCaseData.getReasonableAdjustmentsLetters();

        if (APPELLANT.equals(letterType)) {
            List<Correspondence> correspondenceList = sscsCaseData.getReasonableAdjustmentsLetters() != null && sscsCaseData.getReasonableAdjustmentsLetters().getAppellant() != null ? sscsCaseData.getReasonableAdjustmentsLetters().getAppellant() : new ArrayList<>();
            reasonableAdjustmentsLetters.setAppellant(buildCorrespondenceList(correspondences, correspondenceList));
        }

        if (APPOINTEE.equals(letterType)) {
            List<Correspondence> correspondenceList = sscsCaseData.getReasonableAdjustmentsLetters() != null && sscsCaseData.getReasonableAdjustmentsLetters().getAppointee() != null ? sscsCaseData.getReasonableAdjustmentsLetters().getAppointee() : new ArrayList<>();
            reasonableAdjustmentsLetters.setAppointee(buildCorrespondenceList(correspondences, correspondenceList));
        }

        if (REPRESENTATIVE.equals(letterType)) {
            List<Correspondence> correspondenceList = sscsCaseData.getReasonableAdjustmentsLetters() != null && sscsCaseData.getReasonableAdjustmentsLetters().getRepresentative() != null ? sscsCaseData.getReasonableAdjustmentsLetters().getRepresentative() : new ArrayList<>();
            reasonableAdjustmentsLetters.setRepresentative(buildCorrespondenceList(correspondences, correspondenceList));
        }

        if (JOINT_PARTY.equals(letterType)) {
            List<Correspondence> correspondenceList = sscsCaseData.getReasonableAdjustmentsLetters() != null && sscsCaseData.getReasonableAdjustmentsLetters().getJointParty() != null ? sscsCaseData.getReasonableAdjustmentsLetters().getJointParty() : new ArrayList<>();
            reasonableAdjustmentsLetters.setJointParty(buildCorrespondenceList(correspondences, correspondenceList));
        }

        if (OTHER_PARTY.equals(letterType)) {
            List<Correspondence> correspondenceList = sscsCaseData.getReasonableAdjustmentsLetters() != null && sscsCaseData.getReasonableAdjustmentsLetters().getOtherParty() != null ? sscsCaseData.getReasonableAdjustmentsLetters().getOtherParty() : new ArrayList<>();
            reasonableAdjustmentsLetters.setOtherParty(buildCorrespondenceList(correspondences, correspondenceList));
        }

        return reasonableAdjustmentsLetters;
    }

    private List<Correspondence> buildCorrespondenceList(List<Correspondence> correspondences, List<Correspondence> existingCorrespondence) {
        List<Correspondence> correspondence = new ArrayList<>(existingCorrespondence);
        correspondence.addAll(correspondences);
        correspondence.sort(Comparator.reverseOrder());

        return correspondence;
    }

    private SscsCaseDetails updateCaseInCcd(SscsCaseData caseData, Long caseId, String eventId, IdamTokens idamTokens, String description) {
        try {
            return ccdService.updateCaseWithoutRetry(caseData, caseId, eventId, "Notification sent", description, idamTokens);
        } catch (CcdException ccdEx) {
            log.error("Failed to update ccd case but carrying on [" + caseId + "] ["
                    + caseData.getCaseReference() + "] with event [" + eventId + "]", ccdEx);
            return SscsCaseDetails.builder().build();
        }
    }

    private SscsCaseDetails updateCaseV2InCcd(Consumer<SscsCaseDetails> caseDetailsConsumer, Long caseId, String eventId, IdamTokens idamTokens, String description) {
        try {
            return updateCcdCaseService.updateCaseV2WithoutRetry(caseId, eventId, "Notification sent", description, idamTokens, caseDetailsConsumer);
        } catch (CcdException ccdEx) {
            log.error("Failed to update ccd case using v2 but carrying on [" + caseId + "] ["
                    + caseId + "] with event [" + eventId + "]", ccdEx);
            return SscsCaseDetails.builder().build();
        }
    }

    private byte[] getSentEmailTemplate() throws IOException {
        InputStream in = getClass().getResourceAsStream("/templates/sent_notification.html");
        return IOUtils.toByteArray(in);
    }

    private byte[] getMergedDocument(List<Pdf> pdfs, Long ccdCaseId) {
        PDFMergerUtility merger = new PDFMergerUtility();
        for (Pdf pdf : pdfs) {
            merger.addSource(new ByteArrayInputStream(pdf.getContent()));
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        merger.setDestinationStream(baos);
        try {
            merger.mergeDocuments(null);
        } catch (IOException e) {
            log.error("Failed to create pdf of letter for {}", ccdCaseId, e);
        }

        return baos.toByteArray();
    }

    @NotNull
    private List<Correspondence> getCorrespondences(byte[] pdf, Correspondence correspondence) {
        String filename = String.format("%s %s.pdf", removeDwpFromStartOfEventName(correspondence.getValue().getEventType()), correspondence.getValue().getSentOn());
        List<SscsDocument> pdfDocuments = pdfStoreService.store(pdf, filename, correspondence.getValue().getCorrespondenceType().name());
        return pdfDocuments.stream().map(doc ->
                correspondence.toBuilder().value(correspondence.getValue().toBuilder()
                        .documentLink(doc.getValue().getDocumentLink())
                        .build()).build()
        ).toList();
    }

    @NotNull
    private List<Correspondence> getCorrespondences(Correspondence correspondence) {
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("body", correspondence.getValue().getBody());
        placeholders.put("subject", correspondence.getValue().getSubject());
        placeholders.put("sentOn", correspondence.getValue().getSentOn());
        placeholders.put("from", correspondence.getValue().getFrom());
        placeholders.put("to", correspondence.getValue().getTo());

        byte[] template;
        try {
            template = getSentEmailTemplate();
        } catch (IOException e) {
            throw new PdfGenerationException("Error getting template", e);
        }

        byte[] pdf = pdfServiceClient.generateFromHtml(template, placeholders);
        String filename = String.format("%s %s.pdf", removeDwpFromStartOfEventName(correspondence.getValue().getEventType()), correspondence.getValue().getSentOn());
        List<SscsDocument> pdfDocuments = pdfStoreService.store(pdf, filename, correspondence.getValue().getCorrespondenceType().name());
        final List<Correspondence> correspondences = pdfDocuments.stream().map(doc ->
                correspondence.toBuilder().value(correspondence.getValue().toBuilder()
                        .documentLink(doc.getValue().getDocumentLink())
                        .build()).build()
        ).toList();
        return correspondences;
    }

    private String removeDwpFromStartOfEventName(String eventType) {
        return eventType.startsWith("dwp")
            ? eventType.substring(3, 4).toLowerCase() + eventType.substring(4)
            : eventType;
    }
}
