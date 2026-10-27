package cn.communityelderly.modules.auth.service;

import cn.communityelderly.modules.auth.dto.StaffCreateRequest;
import cn.communityelderly.modules.auth.dto.StaffRolesUpdateRequest;
import cn.communityelderly.modules.auth.dto.StaffSummaryVO;

import java.util.List;

public interface StaffManagementService {

    List<StaffSummaryVO> listStaff();

    StaffSummaryVO createStaff(StaffCreateRequest request);

    StaffSummaryVO replaceRoles(Long userId, StaffRolesUpdateRequest request);
}
