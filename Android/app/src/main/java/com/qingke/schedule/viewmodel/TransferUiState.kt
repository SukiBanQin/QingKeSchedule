package com.qingke.schedule.viewmodel

import com.qingke.schedule.transfer.ScheduleImportPreview

/**
 * P4／A10: everything the "05 数据备份" section and its dialogs render. The preview is the only handle to a
 * pending destructive replace; the committed schedule, the local preferences and the reminder registry are
 * deliberately not represented here.
 */
data class TransferUiState(
    val preview: ScheduleImportPreview? = null,
    /** Read or decode failure: nothing was previewed and nothing was written. */
    val importFailure: String? = null,
    /** A write failure while [preview] is still pending, so the same replace stays retryable. */
    val writeFailure: String? = null,
    val exportFailure: String? = null,
    val statusMessage: String? = null,
    val isWriting: Boolean = false,
) {
    val showsPreview: Boolean get() = preview != null && writeFailure == null
    val showsImportFailure: Boolean get() = preview == null && importFailure != null
    val showsWriteRetry: Boolean get() = preview != null && writeFailure != null
    val showsExportFailure: Boolean get() = preview == null && importFailure == null && exportFailure != null

    /**
     * P4／A10-R1: true exactly while one of the four transfer dialogs is rendered. The host derives both the
     * dialog and the system-back registration from this one value, so back is never consumed when no transfer
     * prompt is visible.
     */
    val showsPrompt: Boolean get() = preview != null || importFailure != null || exportFailure != null
}
