package uk.gov.hmcts.reform.sscs.service;

import static java.lang.String.join;

import java.net.URI;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.ccd.document.am.feign.CaseDocumentClient;
import uk.gov.hmcts.reform.ccd.document.am.model.Document;
import uk.gov.hmcts.reform.ccd.document.am.model.UploadResponse;
import uk.gov.hmcts.reform.sscs.document.EvidenceDownloadClientApi;
import uk.gov.hmcts.reform.sscs.exception.UnsupportedDocumentTypeException;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@Service
@Slf4j
public class EvidenceManagementSecureDocStoreService {

    private final CaseDocumentClient caseDocumentClient;
    private final EvidenceDownloadClientApi evidenceDownloadClientApi;

    @Autowired
    public EvidenceManagementSecureDocStoreService(CaseDocumentClient caseDocumentClient,
                                                   EvidenceDownloadClientApi evidenceDownloadClientApi) {
        this.caseDocumentClient = caseDocumentClient;
        this.evidenceDownloadClientApi = evidenceDownloadClientApi;
    }

    public UploadResponse upload(List<MultipartFile> files, IdamTokens idamTokens) {

        try {
            return caseDocumentClient.uploadDocuments(idamTokens.getIdamOauth2Token(), idamTokens.getServiceAuthorization(), "Benefit", "SSCS", files);
        } catch (HttpClientErrorException httpClientErrorException) {
            log.error("Secure Doc Store service failed to upload documents...", httpClientErrorException);
            if (null != files) {
                logFiles(files);
            }
            throw new UnsupportedDocumentTypeException(httpClientErrorException);
        }
    }

    public byte[] download(String selfHref, IdamTokens idamTokens) {

        try {
            final String userRoles = join(",", idamTokens.getRoles());
            final Document documentMetadata = caseDocumentClient.getMetadataForDocument(idamTokens.getIdamOauth2Token(), idamTokens.getServiceAuthorization(), selfHref);

            ResponseEntity<Resource> responseEntity = evidenceDownloadClientApi.downloadBinary(
                idamTokens.getIdamOauth2Token(),
                idamTokens.getServiceAuthorization(),
                idamTokens.getUserId(),
                userRoles,
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
