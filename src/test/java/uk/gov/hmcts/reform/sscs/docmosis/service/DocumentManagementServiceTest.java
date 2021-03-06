package uk.gov.hmcts.reform.sscs.docmosis.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.docmosis.domain.DocumentHolder;
import uk.gov.hmcts.reform.sscs.docmosis.domain.Pdf;
import uk.gov.hmcts.reform.sscs.docmosis.domain.Template;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.service.CcdPdfService;

public class DocumentManagementServiceTest {

    @Mock
    private PdfGenerationService pdfGenerationService;

    @Mock
    private CcdPdfService ccdPdfService;

    @Mock
    private IdamService idamService;

    private DocumentManagementService documentManagementService;

    @Before
    public void setup() {
        initMocks(this);
        documentManagementService = new DocumentManagementService(pdfGenerationService, ccdPdfService, idamService);
    }

    @Test
    public void givenACaseDataAndTemplateData_thenCreateAPdfAndAddToCaseInCcd() {
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("Test", "Value");
        DocumentHolder holder = DocumentHolder.builder().placeholders(placeholders).template(new Template("bla", "dl6")).build();
        byte[] pdfBytes = {1};
        String docName = "dl6-12345678.pdf";
        IdamTokens tokens = IdamTokens.builder().build();

        given(pdfGenerationService.generatePdf(holder)).willReturn(pdfBytes);
        given(idamService.getIdamTokens()).willReturn(tokens);

        SscsCaseData caseData = SscsCaseData.builder().ccdCaseId("12345678").build();

        Pdf result = documentManagementService.generateDocumentAndAddToCcd(holder, caseData);

        verify(ccdPdfService).mergeDocIntoCcd(docName, pdfBytes, 12345678L, caseData, tokens,  "Uploaded " + docName + " into SSCS", holder.getTemplate().getHmctsDocName());

        assertEquals("Pdf should be as expected", new Pdf(pdfBytes, docName), result);
    }
}
