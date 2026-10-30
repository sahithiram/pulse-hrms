package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.BalanceApplyRequest;
import com.mentis.hrms.pm.dto.request.BalancePreviewRequest;
import com.mentis.hrms.pm.dto.response.BalanceApplyResponse;
import com.mentis.hrms.pm.dto.response.BalancePreviewResponse;

public interface WorkloadBalanceService {

    /** Read-only proposal. Writes nothing, notifies nobody. */
    BalancePreviewResponse preview(BalancePreviewRequest req, Long actorPk);

    /** Applies ONLY the moves in the request, re-validating each one at apply time. */
    BalanceApplyResponse apply(BalanceApplyRequest req, Long actorPk);
}