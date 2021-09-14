package uk.gov.hmcts.reform.sscs.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.springframework.http.MediaType.APPLICATION_PDF;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import uk.gov.hmcts.reform.ccd.document.am.model.UploadResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.DocumentLink;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocument;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocumentDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocumentTranslationStatus;
import uk.gov.hmcts.reform.sscs.domain.pdf.ByteArrayMultipartFile;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@Service
@Slf4j
public class PdfStoreService {
    private final EvidenceManagementService evidenceManagementService;
    private final EvidenceManagementSecureDocStoreService evidenceManagementSecureDocStoreService;
    private final boolean secureDocStoreEnabled;
    private final IdamService idamService;
    static final String DM_STORE_USER_ID = "sscs";

    @Autowired
    public PdfStoreService(EvidenceManagementService evidenceManagementService,
                           EvidenceManagementSecureDocStoreService evidenceManagementSecureDocStoreService,
                           @Value("${feature.secure-doc-store.enabled:false}") boolean secureDocStoreEnabled,
                           IdamService idamService) {
        this.evidenceManagementService = evidenceManagementService;
        this.evidenceManagementSecureDocStoreService = evidenceManagementSecureDocStoreService;
        this.secureDocStoreEnabled = secureDocStoreEnabled;
        this.idamService = idamService;
    }

    public List<SscsDocument> store(byte[] content, String fileName, String documentType) {
        return this.store(content, fileName, documentType, null);
    }

    public List<SscsDocument> store(byte[] content, String fileName, String documentType, SscsDocumentTranslationStatus documentTranslationStatus) {
        if (secureDocStoreEnabled) {
            return storeSecureDocStore(content, fileName, documentType, documentTranslationStatus);
        }
        ByteArrayMultipartFile file = ByteArrayMultipartFile.builder().content(content).name(fileName)
                .contentType(APPLICATION_PDF).build();
        try {
            log.info("Storing file {} of type {} into docstore", fileName, documentType);
            uk.gov.hmcts.reform.document.domain.UploadResponse upload = evidenceManagementService.upload(singletonList(file), "sscs");
            String location = upload.getEmbedded().getDocuments().get(0).links.self.href;

            DocumentLink documentLink = DocumentLink.builder().documentUrl(location).build();
            SscsDocumentDetails sscsDocumentDetails = SscsDocumentDetails.builder()
                    .documentFileName(fileName)
                    .documentDateAdded(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE))
                    .documentLink(documentLink)
                    .documentType(documentType)
                    .documentTranslationStatus(documentTranslationStatus)
                    .build();
            SscsDocument pdfDocument = SscsDocument.builder().value(sscsDocumentDetails).build();

            return singletonList(pdfDocument);
        } catch (RestClientException e) {
            log.error("Failed to store pdf document but carrying on [" + fileName + "]", e);
            return emptyList();
        }
    }

    public List<SscsDocument> storeSecureDocStore(byte[] content, String fileName, String documentType, SscsDocumentTranslationStatus documentTranslationStatus) {
        ByteArrayMultipartFile file = ByteArrayMultipartFile.builder().content(content).name(fileName)
                .contentType(APPLICATION_PDF).build();
        try {
            log.info("Storing file {} of type {} into secure docstore", fileName, documentType);
            IdamTokens idamTokens = idamService.getIdamTokens();
            UploadResponse upload = evidenceManagementSecureDocStoreService.upload(singletonList(file), idamTokens);
            String location = upload.getDocuments().get(0).links.self.href;

            DocumentLink documentLink = DocumentLink.builder().documentUrl(location).build();
            SscsDocumentDetails sscsDocumentDetails = SscsDocumentDetails.builder()
                    .documentFileName(fileName)
                    .documentDateAdded(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE))
                    .documentLink(documentLink)
                    .documentType(documentType)
                    .documentTranslationStatus(documentTranslationStatus)
                    .build();
            SscsDocument pdfDocument = SscsDocument.builder().value(sscsDocumentDetails).build();

            return singletonList(pdfDocument);
        } catch (RestClientException e) {
            log.error("Failed to store pdf document but carrying on [" + fileName + "]", e);
            return emptyList();
        }
    }

    public byte[] download(String href) {
        if (secureDocStoreEnabled) {
            log.info("Downloading file {} from secure docstore", href);
            return evidenceManagementSecureDocStoreService.download(href, idamService.getIdamTokens());
        } else {
            log.info("Downloading file {} from docstore", href);
            return evidenceManagementService.download(URI.create(href), DM_STORE_USER_ID);
        }
    }
}
