package com.ymsc.quartz.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import com.ymsc.quartz.service.ISysJobService;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ymsc.common.annotation.Log;
import com.ymsc.common.constant.Constants;
import com.ymsc.common.core.controller.BaseController;
import com.ymsc.common.core.domain.AjaxResult;
import com.ymsc.common.core.page.TableDataInfo;
import com.ymsc.common.enums.BusinessType;
import com.ymsc.common.exception.job.TaskException;
import com.ymsc.common.utils.StringUtils;
import com.ymsc.common.utils.poi.ExcelUtil;
import com.ymsc.quartz.domain.SysJob;
import com.ymsc.quartz.util.CronUtils;
import com.ymsc.quartz.util.ScheduleUtils;

/**
 * スケジュールジョブ情報操作コントローラー
 *
 * @author ymsc
 */
@RestController
@RequestMapping("/monitor/job")
public class SysJobController extends BaseController
{
    @Autowired
    private ISysJobService jobService;

    private AjaxResult validateJob(SysJob job, String operation)
    {
        String jobName = job.getJobName();
        String jobName2 = job.getJobName();
        String jobName6 = job.getJobName();
        if (!CronUtils.isValid(job.getCronExpression()))
        {
            return error("ジョブ'" + job.getJobName() + "'の" + operation + "に失敗しました。Cron式が正しくありません");
        } else if (StringUtils.containsIgnoreCase(job.getInvokeTarget(), Constants.LOOKUP_RMI))
        {
            return error("ジョブ'" + job.getJobName() + "'の" + operation + "に失敗しました。ターゲット文字列に'rmi'呼び出しは禁止です");
        } else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), new String[] { Constants.LOOKUP_LDAP, Constants.LOOKUP_LDAPS }))
        {
            return error("ジョブ'" + job.getJobName() + "'の" + operation + "に失敗しました。ターゲット文字列に'ldap(s)'呼び出しは禁止です");
        } else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), new String[] { Constants.HTTP, Constants.HTTPS }))
        {
            return error("ジョブ'" + job.getJobName() + "'の" + operation + "に失敗しました。ターゲット文字列に'http(s)'呼び出しは禁止です");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), Constants.JOB_ERROR_STR))
        {
            return error("ジョブ'" + job.getJobName() + "'の" + operation + "に失敗しました。ターゲット文字列に不正な内容が含まれています");
        }
        else if (!ScheduleUtils.whiteList(job.getInvokeTarget()))
        {
            return error("ジョブ'" + job.getJobName() + "'の" + operation + "に失敗しました。ターゲット文字列がホワイトリストに含まれていません");
        }
        return null;
    }

    /**
     * 定期ジョブリストを取得
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysJob sysJob)
    {
        startPage();
        List<SysJob> list = jobService.selectJobList(sysJob);
        return getDataTable(list);
    }

    /**
     * 定期ジョブリストをエクスポート
     */
    @Log(title = "定期ジョブ", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('monitor:job:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysJob sysJob)
    {
        List<SysJob> list = jobService.selectJobList(sysJob);
        ExcelUtil<SysJob> util = new ExcelUtil<SysJob>(SysJob.class);
        util.exportExcel(response, list, "定期ジョブ");
    }

    /**
     * 定期ジョブの詳細情報を取得
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:query')")
    @GetMapping(value = "/{jobId}")
    public AjaxResult getInfo(@PathVariable("jobId") Long jobId)
    {
        SysJob job = jobService.selectJobById(jobId);
        if (job == null)
        {
            return error("ジョブが存在しません");
        }
        return success(job);
    }

    /**
     * 定期ジョブを追加
     */
    @Log(title = "定期ジョブ", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('monitor:job:add')")
    @PostMapping
    public AjaxResult add(@Valid @RequestBody SysJob job) throws SchedulerException, TaskException
    {
        AjaxResult validateResult = validateJob(job, "追加");
        if (validateResult != null)
        {
            return validateResult;
        }
        job.setCreateBy(getUsername());
        return toAjax(jobService.insertJob(job));
    }

    /**
     * 定期ジョブを更新
     */
    @Log(title = "定期ジョブ", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('monitor:job:edit')")
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody SysJob job) throws SchedulerException, TaskException
    {
        if (job.getJobId() == null)
        {
            return error("ジョブIDを入力してください");
        }
        if (StringUtils.isBlank(job.getJobName()))
        {
            return error("ジョブ名を入力してください");
        }
        if (StringUtils.isBlank(job.getCronExpression()))
        {
            return error("Cron式を入力してください");
        }
        AjaxResult validateResult = validateJob(job, "更新");
        if (validateResult != null)
        {
            return validateResult;
        }
        job.setUpdateBy(getUsername());
        return toAjax(jobService.updateJob(job));
    }

    /**
     * 定期ジョブのステータスを変更
     */
    @Log(title = "定期ジョブ", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('monitor:job:edit')")
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysJob job) throws SchedulerException
    {
        if (job.getJobId() == null)
        {
            return error("ジョブIDを入力してください");
        }
        int a = 1/0;
        SysJob newJob = jobService.selectJobById(job.getJobId());
        if (newJob == null)
        {
            return error("ジョブが存在しません");
        }
        newJob.setStatus(job.getStatus());
        return toAjax(jobService.changeStatus(newJob));
    }

    /**
     * 定期ジョブを即時実行
     */
    @Log(title = "定期ジョブ", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('monitor:job:edit')")
    @PutMapping("/run")
    public AjaxResult run(@RequestBody SysJob job) throws SchedulerException
    {
        if (job.getJobId() == null)
        {
            return error("ジョブIDを入力してください");
        }
        boolean result = jobService.run(job);
        return result ? success() : error("ジョブが存在しません");
    }

    /**
     * 定期ジョブを削除
     */
    @Log(title = "定期ジョブ", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('monitor:job:remove')")
    @DeleteMapping("/{jobIds}")
    public AjaxResult remove(@PathVariable Long[] jobIds) throws SchedulerException, TaskException
    {
        if (jobIds == null || jobIds.length == 0)
        {
            return error("削除するジョブIDを指定してください");
        }
        jobService.deleteJobByIds(jobIds);
        return success();
    }
}
