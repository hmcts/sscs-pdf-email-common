package uk.gov.hmcts.reform.sscs.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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
import uk.gov.hmcts.reform.ccd.document.am.feign.CaseDocumentClientApi;
import uk.gov.hmcts.reform.ccd.document.am.model.Document;
import uk.gov.hmcts.reform.ccd.document.am.model.UploadResponse;
import uk.gov.hmcts.reform.sscs.document.EvidenceDownloadClientApi;
import uk.gov.hmcts.reform.sscs.exception.UnsupportedDocumentTypeException;

public class EvidenceManagementSecureDocStoreServiceTest {

    public static final String SERVICE_AUTHORIZATION = "service-authorization";
    public static final String SSCS_USER = "sscs";

    @Mock
    private AuthTokenGenerator authTokenGenerator;
    @Mock
    private CaseDocumentClientApi caseDocumentClientApi;
    @Mock
    private EvidenceDownloadClientApi evidenceDownloadClientApi;

    private EvidenceManagementSecureDocStoreService evidenceManagementSecureDocStoreService;

    @Before
    public void setUp() {
        openMocks(this);
        evidenceManagementSecureDocStoreService = new EvidenceManagementSecureDocStoreService(authTokenGenerator, caseDocumentClientApi, evidenceDownloadClientApi);
    }

    @Test
    public void uploadDocumentShouldCallUploadDocumentManagementClient() {

        List<MultipartFile> files = Collections.emptyList();

        UploadResponse expectedUploadResponse = mock(UploadResponse.class);

        when(authTokenGenerator.generate()).thenReturn(SERVICE_AUTHORIZATION);
        when(caseDocumentClientApi.uploadDocuments(any(), eq(SERVICE_AUTHORIZATION), any()))
                .thenReturn(expectedUploadResponse);

        UploadResponse actualUploadedResponse = evidenceManagementSecureDocStoreService.upload(files);

        verify(caseDocumentClientApi, times(1))
                .uploadDocuments(any(), eq(SERVICE_AUTHORIZATION), any());

        assertEquals(actualUploadedResponse, expectedUploadResponse);
    }

    @Test(expected = UnsupportedDocumentTypeException.class)
    public void uploadDocumentShouldThrowUnSupportedDocumentTypeExceptionIfAnyGivenDocumentTypeIsNotSupportedByDocumentStore() {
        List<MultipartFile> files = mockMultipartFiles();

        when(authTokenGenerator.generate()).thenReturn(SERVICE_AUTHORIZATION);
        when(caseDocumentClientApi.uploadDocuments(any(), eq(SERVICE_AUTHORIZATION), any()))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY));

        evidenceManagementSecureDocStoreService.upload(files);

    }

    private List<MultipartFile> mockMultipartFiles() {
        MultipartFile value = mock(MultipartFile.class);
        when(value.getName()).thenReturn("testFile.txt");
        when(value.getOriginalFilename()).thenReturn("OriginalTestFile.txt");

        List<MultipartFile> files = new ArrayList<>();
        files.add(value);
        return files;
    }

    @Test(expected = Exception.class)
    public void uploadDocumentShouldRethrowAnyExceptionIfItsNotHttpClientErrorException() {
        List<MultipartFile> files = Collections.emptyList();

        when(authTokenGenerator.generate()).thenReturn(SERVICE_AUTHORIZATION);
        when(caseDocumentClientApi.uploadDocuments(any(), eq(SERVICE_AUTHORIZATION), any()))
                .thenThrow(new Exception("AppealNumber"));

        evidenceManagementSecureDocStoreService.upload(files);
    }

    @Test
    public void downloadDocumentShouldDownloadSpecifiedDocument() {
        ResponseEntity<Resource> mockResponseEntity = mock(ResponseEntity.class);
        ByteArrayResource stubbedResource = new ByteArrayResource(new byte[] {});
        when(mockResponseEntity.getBody()).thenReturn(stubbedResource);


        Document.Link stubbedLink = new Document.Link();
        stubbedLink.href = "http://localhost:4506/documents/eb8cbfaa-37c3-4644-aa77-b9a2e2c72332";
        Document.Links stubbedLinks = new Document.Links();
        stubbedLinks.binary = stubbedLink;
        Document stubbedDocument = Document.builder().links(stubbedLinks).build();

        when(authTokenGenerator.generate()).thenReturn(SERVICE_AUTHORIZATION);
        when(caseDocumentClientApi.getMetadataForDocument(anyString(), anyString(), any())).thenReturn(stubbedDocument);
        when(evidenceDownloadClientApi.downloadBinary(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(mockResponseEntity);

        evidenceManagementSecureDocStoreService.download(UUID.randomUUID(), SSCS_USER);

        verify(mockResponseEntity, times(1)).getBody();
    }

    @Test
    public void downloadDocumentWhenBodyIsEmpty() {
        ResponseEntity<Resource> mockResponseEntity = mock(ResponseEntity.class);
        when(mockResponseEntity.getBody()).thenReturn(null);

        Document.Link stubbedLink = new Document.Link();
        stubbedLink.href = "http://localhost:4506/documents/eb8cbfaa-37c3-4644-aa77-b9a2e2c72332";
        Document.Links stubbedLinks = new Document.Links();
        stubbedLinks.binary = stubbedLink;
        Document stubbedDocument = Document.builder().links(stubbedLinks).build();


        when(authTokenGenerator.generate()).thenReturn(SERVICE_AUTHORIZATION);
        when(caseDocumentClientApi.getMetadataForDocument(anyString(), anyString(), any())).thenReturn(stubbedDocument);
        when(evidenceDownloadClientApi.downloadBinary(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(mockResponseEntity);

        evidenceManagementSecureDocStoreService.download(UUID.randomUUID(), SSCS_USER);

        verify(mockResponseEntity, times(1)).getBody();
    }

    @Test(expected = UnsupportedDocumentTypeException.class)
    public void downloadDocumentShoudlThrowExceptionWhenDocumentNotFound() {
        ResponseEntity<Resource> mockResponseEntity = mock(ResponseEntity.class);
        ByteArrayResource stubbedResource = new ByteArrayResource(new byte[] {});
        when(mockResponseEntity.getBody()).thenReturn(stubbedResource);

        Document.Link stubbedLink = new Document.Link();
        stubbedLink.href = "http://localhost:4506/documents/eb8cbfaa-37c3-4644-aa77-b9a2e2c72332";
        Document.Links stubbedLinks = new Document.Links();
        stubbedLinks.binary = stubbedLink;
        Document stubbedDocument = Document.builder().links(stubbedLinks).build();

        when(authTokenGenerator.generate()).thenReturn(SERVICE_AUTHORIZATION);
        when(caseDocumentClientApi.getMetadataForDocument(anyString(), anyString(), any())).thenReturn(stubbedDocument);
        when(evidenceDownloadClientApi.downloadBinary(anyString(), anyString(), anyString(), anyString(), anyString())).thenThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY));

        evidenceManagementSecureDocStoreService.download(UUID.randomUUID(), SSCS_USER);
    }

    @Test(expected = Exception.class)
    public void downloadDocumentShouldRethrowAnyExceptionIfItsNotHttpClientErrorException() {
        List<MultipartFile> files = Collections.emptyList();

        Document.Link stubbedLink = new Document.Link();
        stubbedLink.href = "http://localhost:4506/documents/eb8cbfaa-37c3-4644-aa77-b9a2e2c72332";
        Document.Links stubbedLinks = new Document.Links();
        stubbedLinks.binary = stubbedLink;
        Document stubbedDocument = Document.builder().links(stubbedLinks).build();

        when(authTokenGenerator.generate()).thenReturn(SERVICE_AUTHORIZATION);
        when(caseDocumentClientApi.getMetadataForDocument(anyString(), anyString(), any())).thenReturn(stubbedDocument);
        when(evidenceDownloadClientApi.downloadBinary(anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenThrow(new Exception("AppealNumber"));

        evidenceManagementSecureDocStoreService.download(UUID.randomUUID(), SSCS_USER);
    }
}
