package cadonuno.pipelinescanautotrigger;

import cadonuno.pipelinescanautotrigger.pipelinescan.PipelineScanWrapper;
import com.intellij.dvcs.push.PrePushHandler;
import com.intellij.dvcs.push.PushInfo;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PipelineScanAutoPrePushHandler implements PrePushHandler {
    @Override
    public @NotNull
    @Nls(capitalization = Nls.Capitalization.Title) String getPresentableName() {
        return "Pipeline Scan Auto Trigger Listener";
    }

    @Override
    public @NotNull Result handle(@NotNull List<PushInfo> list, @NotNull ProgressIndicator progressIndicator) {
        if (list.isEmpty()) {
            return Result.OK;
        }
        try (PipelineScanWrapper pipelineScanWrapper = PipelineScanWrapper.acquire()) {
            //call scan

            //analyze filtered_results json
            //TODO: implement way of picking file and fail criteria

            // if filtered results is not empty:
            return Result.ABORT_AND_CLOSE;
        }
    }
}