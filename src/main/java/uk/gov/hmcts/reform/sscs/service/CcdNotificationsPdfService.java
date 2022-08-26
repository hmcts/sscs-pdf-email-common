package uk.gov.hmcts.reform.sscs.service;

import static java.util.Objects.isNull;
import static uk.gov.hmcts.reform.sscs.model.LetterType.APPELLANT;
import static uk.gov.hmcts.reform.sscs.model.LetterType.APPOINTEE;
import static uk.gov.hmcts.reform.sscs.model.LetterType.JOINT_PARTY;
import static uk.gov.hmcts.reform.sscs.model.LetterType.OTHER_PARTY;
import static uk.gov.hmcts.reform.sscs.model.LetterType.REPRESENTATIVE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.pdf.service.client.PDFServiceClient;
import uk.gov.hmcts.reform.sscs.ccd.domain.AbstractDocument;
import uk.gov.hmcts.reform.sscs.ccd.domain.AbstractDocumentDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.Correspondence;
import uk.gov.hmcts.reform.sscs.ccd.domain.CorrespondenceDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.EventType;
import uk.gov.hmcts.reform.sscs.ccd.domain.ReasonableAdjustmentsLetters;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocument;
import uk.gov.hmcts.reform.sscs.ccd.exception.CcdException;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.docmosis.domain.Pdf;
import uk.gov.hmcts.reform.sscs.exception.PdfGenerationException;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.model.LetterType;

@Service
@Slf4j
@RequiredArgsConstructor
public class CcdNotificationsPdfService {

    public static final String NOTIFICATION_SUMMARY_TEMPLATE = "%s %s Notification Successfully Sent to %s";
    public static final String NOTIFICATION_DESCRIPTION_TEMPLATE = "The %s Notification for the %s event was "
        + "Successfully Sent to %s from %s.\nDocuments:\n%s";
    public static final String URL_LINK_TEMPLATE = "* [%s](%s)";
    public static final String FILENAME_TEMPLATE = "%s %s.pdf";
    public static final String SENT_NOTIFICATION_HTML_TEMPLATE_PATH = "/templates/sent_notification.html";

    private final PdfStoreService pdfStoreService;

    private final PDFServiceClient pdfServiceClient;

    private final CcdService ccdService;

    private final IdamService idamService;


    public SscsCaseData mergeCorrespondenceIntoCcd(SscsCaseData sscsCaseData, Correspondence correspondence) {
        Map<String, Object> placeholders = new HashMap<>();
        CorrespondenceDetails details = correspondence.getValue();
        placeholders.put("body", details.getBody());
        placeholders.put("subject", details.getSubject());
        placeholders.put("sentOn", details.getSentOn());
        placeholders.put("from", details.getFrom());
        placeholders.put("to", details.getTo());

        byte[] template;
        try {
            InputStream inputStream = getClass().getResourceAsStream(SENT_NOTIFICATION_HTML_TEMPLATE_PATH);
            assert inputStream != null;
            template = IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            log.error("Error getting template for caseId {}", sscsCaseData.getCcdCaseId(), e);
            throw new PdfGenerationException("Error getting template", e);
        }

        byte[] pdf = pdfServiceClient.generateFromHtml(template, placeholders);

        return correspondenceUpdateCaseInCcd(sscsCaseData, correspondence, pdf);
    }

    public SscsCaseData mergeLetterCorrespondenceIntoCcd(byte[] pdf, Long ccdCaseId, Correspondence correspondence) {
        final SscsCaseDetails sscsCaseDetails = ccdService.getByCaseId(ccdCaseId, idamService.getIdamTokens());

        return correspondenceUpdateCaseInCcd(sscsCaseDetails.getData(), correspondence, pdf);
    }

    public SscsCaseData mergeReasonableAdjustmentsCorrespondenceIntoCcd(List<Pdf> pdfs, Long ccdCaseId, Correspondence correspondence, LetterType letterType) {
        PDFMergerUtility merger = new PDFMergerUtility();
        for (Pdf pdf : pdfs) {
            merger.addSource(new ByteArrayInputStream(pdf.getContent()));
        }
        ByteArrayOutputStream byteArrayOutputSteam = new ByteArrayOutputStream();
        merger.setDestinationStream(byteArrayOutputSteam);
        try {
            merger.mergeDocuments(null);
        } catch (IOException e) {
            log.error("Failed to create pdf of letter for {}", ccdCaseId, e);
        }

        byte[] letterDocument = byteArrayOutputSteam.toByteArray();

        return mergeReasonableAdjustmentsCorrespondenceIntoCcd(letterDocument, ccdCaseId, correspondence, letterType);
    }

    public SscsCaseData mergeReasonableAdjustmentsCorrespondenceIntoCcd(byte[] letterDocument, Long ccdCaseId, Correspondence correspondence, LetterType letterType) {
        CorrespondenceDetails details = correspondence.getValue();
        String filename = String.format(FILENAME_TEMPLATE, details.getEventType(), details.getSentOn());

        List<SscsDocument> pdfDocuments = pdfStoreService.store(letterDocument, filename, details.getCorrespondenceType().name());

        final List<Correspondence> correspondences = mapPdfDocumentsToCorrespondences(correspondence, pdfDocuments);

        final SscsCaseDetails sscsCaseDetails = ccdService.getByCaseId(ccdCaseId, idamService.getIdamTokens());
        final SscsCaseData sscsCaseData = sscsCaseDetails.getData();

        setReasonableAdjustmentsCorrespondenceByParty(sscsCaseData, correspondences, letterType);
        sscsCaseData.updateReasonableAdjustmentsOutstanding();

        log.info("Creating a reasonable adjustment for {}", ccdCaseId);

        return updateCaseInCcd(sscsCaseData, EventType.STOP_BULK_PRINT_FOR_REASONABLE_ADJUSTMENT.getCcdType(),
            "Notification sent, Stopped bulk print",
            "Notification sent, Stopped for reasonable adjustment to be sent");
    }

    @NotNull
    private List<Correspondence> mapPdfDocumentsToCorrespondences(Correspondence correspondence,
                                                                  List<SscsDocument> pdfDocuments) {
        return pdfDocuments.stream()
            .map(doc -> correspondence.toBuilder()
                .value(correspondence.getValue().toBuilder()
                    .documentLink(doc.getValue().getDocumentLink())
                    .build())
                .build())
            .collect(Collectors.toList());
    }

    private void setReasonableAdjustmentsCorrespondenceByParty(SscsCaseData sscsCaseData,
                                                               List<Correspondence> correspondences,
                                                               LetterType letterType) {
        ReasonableAdjustmentsLetters reasonableAdjustmentsLetters = sscsCaseData.getReasonableAdjustmentsLetters();
        if (isNull(reasonableAdjustmentsLetters)) {
            reasonableAdjustmentsLetters = ReasonableAdjustmentsLetters.builder().build();
        }

        if (APPELLANT == letterType) {
            reasonableAdjustmentsLetters.setAppellant(
                buildCorrespondenceList(correspondences, reasonableAdjustmentsLetters.getAppellant()));
        } else if (APPOINTEE == letterType) {
            reasonableAdjustmentsLetters.setAppointee(
                buildCorrespondenceList(correspondences, reasonableAdjustmentsLetters.getAppointee()));
        } else if (REPRESENTATIVE == letterType) {
            reasonableAdjustmentsLetters.setRepresentative(
                buildCorrespondenceList(correspondences, reasonableAdjustmentsLetters.getRepresentative()));
        } else if (JOINT_PARTY == letterType) {
            reasonableAdjustmentsLetters.setJointParty(
                buildCorrespondenceList(correspondences, reasonableAdjustmentsLetters.getJointParty()));
        } else if (OTHER_PARTY == letterType) {
            reasonableAdjustmentsLetters.setOtherParty(
                buildCorrespondenceList(correspondences, reasonableAdjustmentsLetters.getOtherParty()));
        }

        sscsCaseData.setReasonableAdjustmentsLetters(reasonableAdjustmentsLetters);
    }

    private List<Correspondence> buildCorrespondenceList(List<Correspondence> correspondences,
                                                         List<Correspondence> existingCorrespondence) {
        if (isNull(existingCorrespondence)) {
            existingCorrespondence = new ArrayList<>();
        }
        return Stream.concat(existingCorrespondence.stream(), correspondences.stream())
            .sorted(Comparator.reverseOrder())
            .collect(Collectors.toList());
    }

    private SscsCaseData correspondenceUpdateCaseInCcd(SscsCaseData caseData, Correspondence correspondence,
                                                       byte[] pdf) {
        CorrespondenceDetails details = correspondence.getValue();

        String filename = String.format(FILENAME_TEMPLATE, details.getEventType(), details.getSentOn());
        List<SscsDocument> pdfDocuments = pdfStoreService.store(pdf, filename, details.getCorrespondenceType().name());
        addDocumentsToCaseData(correspondence, caseData, pdfDocuments);

        String documentLinks = pdfDocuments.stream()
            .map(AbstractDocument::getValue)
            .map(AbstractDocumentDetails::getDocumentLink)
            .map(documentLink -> String.format(URL_LINK_TEMPLATE, documentLink.getDocumentFilename(),
                documentLink.getDocumentUrl()))
            .collect(Collectors.joining("\n"));

        String description = String.format(NOTIFICATION_DESCRIPTION_TEMPLATE,
            details.getCorrespondenceType().name(), details.getEventType(), details.getTo(), details.getFrom(),
            documentLinks);

        String summary = String.format(NOTIFICATION_SUMMARY_TEMPLATE,
            details.getEventType(), details.getCorrespondenceType().name(), details.getTo());

        return updateCaseInCcd(caseData, EventType.NOTIFICATION_SENT.getCcdType(), summary, description);
    }

    private void addDocumentsToCaseData(Correspondence correspondence, SscsCaseData sscsCaseData,
                                        List<SscsDocument> pdfDocuments) {
        final List<Correspondence> correspondences = mapPdfDocumentsToCorrespondences(correspondence, pdfDocuments);
        sscsCaseData.setCorrespondence(buildCorrespondenceList(correspondences, sscsCaseData.getCorrespondence()));
    }

    private SscsCaseData updateCaseInCcd(SscsCaseData caseData, String eventId, String summary, String description) {
        Long caseId = Long.parseLong(caseData.getCcdCaseId());
        try {
            SscsCaseDetails sscsCaseDetails = ccdService.updateCaseWithoutRetry(caseData, caseId, eventId,
                summary, description, idamService.getIdamTokens());
            return sscsCaseDetails.getData();
        } catch (CcdException ccdEx) {
            log.error("Failed to update ccd case for event {} but carrying on with event for caseId {} reference {}",
                eventId, caseId, caseData.getCaseReference(), ccdEx);
            return SscsCaseData.builder().build();
        }
    }

}
