package org.dbsyncer.web.config;

import org.dbsyncer.biz.vo.RestResult;
import org.dbsyncer.common.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@Component
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, 
                       AccessDeniedException e) throws IOException {
        logger.warn("权限不足: {} {} - {}", request.getMethod(), request.getRequestURI(), e.getMessage());

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        RestResult result = RestResult.restFail("权限不足，无法执行此操作", 403);
        
        PrintWriter out = null;
        try {
            out = response.getWriter();
            out.write(JsonUtil.objToJson(result));
            out.flush();
        } finally {
            if (null != out) {
                out.close();
            }
        }
    }
}
