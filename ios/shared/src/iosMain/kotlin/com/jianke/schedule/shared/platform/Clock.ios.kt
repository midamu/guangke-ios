package com.jianke.schedule.shared.platform

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.timeIntervalSince1970

actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

actual fun nowHHmm(): String {
    val f = NSDateFormatter()
    f.dateFormat = "HH:mm"
    return f.stringFromDate(NSDate())
}
