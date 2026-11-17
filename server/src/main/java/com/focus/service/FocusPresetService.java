package com.focus.service;

import com.focus.common.AuthContext;
import com.focus.common.BizException;
import com.focus.dto.FocusPresetReq;
import com.focus.entity.FocusPreset;
import com.focus.mapper.FocusPresetMapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FocusPresetService {
    private final FocusPresetMapper mapper;
    private final BasicDataService basicDataService;
    public FocusPresetService(FocusPresetMapper mapper, BasicDataService basicDataService) {
        this.mapper = mapper; this.basicDataService = basicDataService;
    }
    public List<FocusPreset> list() { return mapper.selectList(AuthContext.userId()); }
    public FocusPreset create(FocusPresetReq req) {
        FocusPreset preset = new FocusPreset(); preset.setUserId(AuthContext.userId()); apply(preset, req);
        mapper.insert(preset); return preset;
    }
    public FocusPreset update(Long id, FocusPresetReq req) {
        FocusPreset preset = require(id); apply(preset, req);
        if (mapper.update(preset) == 0) throw BizException.notFound("常用专注不存在");
        return preset;
    }
    public void delete(Long id) { if (mapper.delete(AuthContext.userId(), id) == 0) throw BizException.notFound("常用专注不存在"); }
    private FocusPreset require(Long id) {
        FocusPreset preset = mapper.selectById(AuthContext.userId(), id);
        if (preset == null) throw BizException.notFound("常用专注不存在"); return preset;
    }
    private void apply(FocusPreset preset, FocusPresetReq req) {
        if (req.getTaskId() != null && req.getHabitId() != null) throw BizException.badRequest("任务和习惯最多关联一个");
        if (req.getTaskId() != null) basicDataService.requireTask(req.getTaskId());
        if (req.getHabitId() != null) basicDataService.requireHabit(req.getHabitId());
        if ("countdown".equals(req.getMode()) && req.getPlannedSec() == null) throw BizException.badRequest("倒计时必须设置时长");
        preset.setName(req.getName().trim()); preset.setMode(req.getMode());
        preset.setPlannedSec("countdown".equals(req.getMode()) ? req.getPlannedSec() : null);
        preset.setTaskId(req.getTaskId()); preset.setHabitId(req.getHabitId());
        preset.setNoteTemplate(req.getNoteTemplate() == null ? "" : req.getNoteTemplate().trim());
        preset.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
    }
}
