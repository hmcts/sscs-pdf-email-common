package uk.gov.hmcts.reform.sscs.service;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.ccd.document.am.feign.CaseDocumentClientApi;
import uk.gov.hmcts.reform.ccd.document.am.model.Classification;
import uk.gov.hmcts.reform.ccd.document.am.model.Document;
import uk.gov.hmcts.reform.ccd.document.am.model.DocumentUploadRequest;
import uk.gov.hmcts.reform.ccd.document.am.model.UploadResponse;
import uk.gov.hmcts.reform.sscs.document.EvidenceDownloadClientApi;
import uk.gov.hmcts.reform.sscs.exception.UnsupportedDocumentTypeException;

@Service
@Slf4j
public class EvidenceManagementSecureDocStoreService {

    public static final String S2S_TOKEN = "oauth2Token";

    private final AuthTokenGenerator authTokenGenerator;
    private final CaseDocumentClientApi caseDocumentClientApi;
    private final EvidenceDownloadClientApi evidenceDownloadClientApi;

    @Autowired
    public EvidenceManagementSecureDocStoreService(
        AuthTokenGenerator authTokenGenerator,
        CaseDocumentClientApi caseDocumentClientApi,
        EvidenceDownloadClientApi evidenceDownloadClientApi
    ) {
        this.authTokenGenerator = authTokenGenerator;
        this.caseDocumentClientApi = caseDocumentClientApi;
        this.evidenceDownloadClientApi = evidenceDownloadClientApi;
    }

    public UploadResponse upload(List<MultipartFile> files) {

        String serviceAuthorization = authTokenGenerator.generate();

        try {
            DocumentUploadRequest documentUploadRequest = new DocumentUploadRequest(Classification.RESTRICTED.name(), "Benefit", "SSCS", files);
            return caseDocumentClientApi.uploadDocuments(S2S_TOKEN, serviceAuthorization, documentUploadRequest);
        } catch (HttpClientErrorException httpClientErrorException) {
            log.error("Secure Doc Store service failed to upload documents...", httpClientErrorException);
            if (null != files) {
                logFiles(files);
            }
            throw new UnsupportedDocumentTypeException(httpClientErrorException);
        }
    }

    public byte[] download(UUID documentId, String userId) {
        String serviceAuthorization = authTokenGenerator.generate();

        try {
            final Document documentMetadata = caseDocumentClientApi.getMetadataForDocument(S2S_TOKEN, serviceAuthorization, documentId);

            ResponseEntity<Resource> responseEntity = evidenceDownloadClientApi.downloadBinary(
                S2S_TOKEN,
                serviceAuthorization,
                userId,
                "caseworker",
                URI.create(documentMetadata.links.binary.href).getPath().replaceFirst("/", "")
            );

            ByteArrayResource resource = (ByteArrayResource) responseEntity.getBody();
            return (resource != null) ? resource.getByteArray() : new byte[0];
        } catch (HttpClientErrorException httpClientErrorException) {
            log.error("Secure Doc Store service failed to download document...", httpClientErrorException);
            throw new UnsupportedDocumentTypeException(httpClientErrorException);
        }
    }

    private void logFiles(List<MultipartFile> files) {
        files.forEach(file -> {
            log.info("Name: {}", file.getName());
            log.info("OriginalName {}", file.getOriginalFilename());
        });
    }

}
