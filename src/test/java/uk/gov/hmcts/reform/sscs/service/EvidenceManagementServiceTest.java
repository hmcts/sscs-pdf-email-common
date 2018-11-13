package uk.gov.hmcts.reform.sscs.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.document.DocumentUploadClientApi;
import uk.gov.hmcts.reform.document.domain.Document;
import uk.gov.hmcts.reform.document.domain.UploadResponse;
import uk.gov.hmcts.reform.sscs.document.EvidenceDownloadClientApi;
import uk.gov.hmcts.reform.sscs.document.EvidenceMetadataDownloadClientApi;
import uk.gov.hmcts.reform.sscs.exception.UnsupportedDocumentTypeException;

public class EvidenceManagementServiceTest {

    public static final String SERVICE_AUTHORIZATION = "service-authorization";

    @Mock
    private AuthTokenGenerator authTokenGenerator;
    @Mock
    private DocumentUploadClientApi documentUploadClientApi;
    @Mock
    private EvidenceMetadataDownloadClientApi evidenceMetadataDownloadClientApi;
    @Mock
    private EvidenceDownloadClientApi evidenceDownloadClientApi;

    private EvidenceManagementService evidenceManagementService;

    @Before
    public void setUp() {
        initMocks(this);
        evidenceManagementService = new EvidenceManagementService(authTokenGenerator, documentUploadClientApi, evidenceDownloadClientApi, evidenceMetadataDownloadClientApi);
    }

    @Test
    public void uploadDocumentShouldCallUploadDocumentManagementClient() {

        List<MultipartFile> files = Collections.emptyList();

        UploadResponse expectedUploadResponse = mock(UploadResponse.class);

        when(authTokenGenerator.generate()).thenReturn(SERVICE_AUTHORIZATION);
        when(documentUploadClientApi.upload(any(), eq(SERVICE_AUTHORIZATION), anyString(), eq(files)))
                .thenReturn(expectedUploadResponse);

        UploadResponse actualUploadedResponse = evidenceManagementService.upload(files);

        verify(documentUploadClientApi, times(1))
                .upload(any(), eq(SERVICE_AUTHORIZATION), anyString(), eq(files));

        assertEquals(actualUploadedResponse, expectedUploadResponse);
    }

    @Test(expected = UnsupportedDocumentTypeException.class)
    public void uploadDocumentShouldThrowUnSupportedDocumentTypeExceptionIfAnyGivenDocumentTypeIsNotSupportedByDocumentStore() {
        List<MultipartFile> files = Collections.emptyList();

        when(authTokenGenerator.generate()).thenReturn(SERVICE_AUTHORIZATION);
        when(documentUploadClientApi.upload(any(), eq(SERVICE_AUTHORIZATION), anyString(), eq(files)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY));

        evidenceManagementService.upload(files);

    }

    @Test(expected = Exception.class)
    public void uploadDocumentShouldRethrowAnyExceptionIfItsNotHttpClientErrorException() {
        List<MultipartFile> files = Collections.emptyList();

        when(authTokenGenerator.generate()).thenReturn(SERVICE_AUTHORIZATION);
        when(documentUploadClientApi.upload(any(), eq(SERVICE_AUTHORIZATION), anyString(), eq(files)))
                .thenThrow(new Exception("AppealNumber"));

        evidenceManagementService.upload(files);
    }

    @Test
    public void downloadDocumentShouldDownloadSpecifiedDocument() {
        ResponseEntity<Resource> mockResponseEntity = mock(ResponseEntity.class);
        ByteArrayResource stubbedResource = new ByteArrayResource(new byte[] {});
        when(mockResponseEntity.getBody()).thenReturn(stubbedResource);


        Document stubbedDocument = new Document();
        Document.Link stubbedLink = new Document.Link();
        stubbedLink.href = "http://localhost:4506/documents/eb8cbfaa-37c3-4644-aa77-b9a2e2c72332";
        Document.Links stubbedLinks = new Document.Links();
        stubbedLinks.binary = stubbedLink;
        stubbedDocument.links = stubbedLinks;

        when(authTokenGenerator.generate()).thenReturn(SERVICE_AUTHORIZATION);
        when(evidenceMetadataDownloadClientApi.getDocumentMetadata(anyString(), anyString(), anyString(), anyString())).thenReturn(stubbedDocument);
        when(evidenceDownloadClientApi.downloadBinary(anyString(), anyString(), anyString(), anyString())).thenReturn(mockResponseEntity);

        evidenceManagementService.download(URI.create("http://localhost:4506/somefile.doc"));

        verify(mockResponseEntity, times(1)).getBody();
    }

    @Test(expected = UnsupportedDocumentTypeException.class)
    public void downloadDocumentShoudlThrowExceptionWhenDocumentNotFound() {
        ResponseEntity<Resource> mockResponseEntity = mock(ResponseEntity.class);
        ByteArrayResource stubbedResource = new ByteArrayResource(new byte[] {});
        when(mockResponseEntity.getBody()).thenReturn(stubbedResource);

        Document stubbedDocument = new Document();
        Document.Link stubbedLink = new Document.Link();
        stubbedLink.href = "http://localhost:4506/documents/eb8cbfaa-37c3-4644-aa77-b9a2e2c72332";
        Document.Links stubbedLinks = new Document.Links();
        stubbedLinks.binary = stubbedLink;
        stubbedDocument.links = stubbedLinks;

        when(authTokenGenerator.generate()).thenReturn(SERVICE_AUTHORIZATION);
        when(evidenceMetadataDownloadClientApi.getDocumentMetadata(anyString(), anyString(), anyString(), anyString())).thenReturn(stubbedDocument);
        when(evidenceDownloadClientApi.downloadBinary(anyString(), anyString(), anyString(), anyString())).thenThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY));

        evidenceManagementService.download(URI.create("http://localhost:4506/somefile.doc"));
    }

    @Test(expected = Exception.class)
    public void downloadDocumentShouldRethrowAnyExceptionIfItsNotHttpClientErrorException() {
        List<MultipartFile> files = Collections.emptyList();

        Document stubbedDocument = new Document();
        Document.Link stubbedLink = new Document.Link();
        stubbedLink.href = "http://localhost:4506/documents/eb8cbfaa-37c3-4644-aa77-b9a2e2c72332";
        Document.Links stubbedLinks = new Document.Links();
        stubbedLinks.binary = stubbedLink;
        stubbedDocument.links = stubbedLinks;

        when(authTokenGenerator.generate()).thenReturn(SERVICE_AUTHORIZATION);
        when(evidenceMetadataDownloadClientApi.getDocumentMetadata(anyString(), anyString(), anyString(), anyString())).thenReturn(stubbedDocument);
        when(evidenceDownloadClientApi.downloadBinary(anyString(), anyString(), anyString(), anyString()))
            .thenThrow(new Exception("AppealNumber"));

        evidenceManagementService.download(URI.create("http://localhost:4506/somefile.doc"));
    }
}
