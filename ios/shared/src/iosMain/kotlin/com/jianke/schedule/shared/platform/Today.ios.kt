package com.jianke.schedule.shared.platform

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

actual fun todayIso(): String {
    val f = NSDateFormatter()
    f.dateFormat = "yyyy-MM-dd"
    return f.stringFromDate(NSDate())
}
