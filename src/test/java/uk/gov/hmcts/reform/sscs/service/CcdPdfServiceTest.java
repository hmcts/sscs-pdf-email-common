package uk.gov.hmcts.reform.sscs.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.hmcts.reform.sscs.ccd.util.CaseDataUtils.buildCaseData;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

public class CcdPdfServiceTest {

    @InjectMocks
    private CcdPdfService service;

    @Mock
    PdfStoreService pdfStoreService;

    @Mock
    CcdService ccdService;

    SscsCaseData caseData = buildCaseData();

    @Before
    public void setup() {
        initMocks(this);
    }

    @Test
    public void mergeValidPdfAndStoreInDocumentStore() {
        byte[] pdf = {};

        service.mergeDocIntoCcd("Myfile.pdf", pdf,1L, caseData, IdamTokens.builder().build());

        verify(pdfStoreService).store(any(), any());
        verify(ccdService).updateCase(any(), any(), any(), any(), any(), any());
    }
}
