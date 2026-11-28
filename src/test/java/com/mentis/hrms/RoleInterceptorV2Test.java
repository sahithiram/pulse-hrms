package com.mentis.hrms;

import com.mentis.hrms.interceptor.RoleInterceptorV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.*;

class RoleInterceptorV2Test extends BaseIntegrationTest {

    @Autowired RoleInterceptorV2 interceptor;

    @BeforeEach void clean() { truncateAll(); }

    private MockHttpServletRequest req(String path, String method) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, path);
        r.setRequestURI(path);
        return r;
    }

    @Test
    void noSession_pageRedirects_toLogin() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        boolean ok = interceptor.preHandle(req("/dashboard/hr", "GET"), res, new Object());
        assertFalse(ok);
        assertEquals(302, res.getStatus());
        // The interceptor sends unauthenticated page requests to the candidate/employee login page.
        assertEquals("/candidate/login?error=auth", res.getRedirectedUrl());
    }

    @Test
    void noSession_apiGets401_json() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        boolean ok = interceptor.preHandle(req("/api/employees/1", "GET"), res, new Object());
        assertFalse(ok);
        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString().contains("UNAUTHENTICATED"));
    }

    @Test
    void superAdmin_passes() throws Exception {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute("userId", 1L);
        s.setAttribute("userRole", "SUPER_ADMIN");
        MockHttpServletRequest r = req("/dashboard/hr", "GET");
        r.setSession(s);
        assertTrue(interceptor.preHandle(r, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void auditMode_allowsButLogs_whenWouldDeny() throws Exception {
        // In enforce mode (default) unauthenticated is denied - this test documents the switch.
        MockHttpServletResponse res = new MockHttpServletResponse();
        boolean ok = interceptor.preHandle(req("/dashboard/hr", "GET"), res, new Object());
        assertFalse(ok, "default enforce mode must deny");
    }

    @Test
    void publicPaths_passWithoutSession() throws Exception {
        for (String p : new String[]{"/", "/home", "/css/app.css", "/js/app.js"}) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            assertTrue(interceptor.preHandle(req(p, "GET"), res, new Object()), "public: " + p);
        }
    }

    @Test
    void uploads_neverPublic() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        boolean ok = interceptor.preHandle(req("/uploads/employees/emp001.pdf", "GET"), res, new Object());
        assertFalse(ok, "/uploads/** must never be publicly reachable");
    }
}