package cn.communityelderly.modules.elder.service;

import cn.communityelderly.common.api.PageResult;
import cn.communityelderly.modules.elder.dto.*;
import cn.communityelderly.modules.elder.entity.ElderProfile;

public interface ElderArchiveService {

    ElderArchiveVO createElder(ElderCreateDTO dto);

    PageResult<ElderArchiveVO> pageElders(ElderQueryDTO query);

    ElderArchiveVO getElderById(Long elderId);

    void updateElderStatus(Long elderId, String status, String reason);

    ElderProfile getElderEntity(Long elderId);

    ElderProfile updateElderProfileSelf(Long elderId, ElderProfileUpdateDTO dto);
}
