package com.jianke.schedule.shared.platform

/** 当前时间（毫秒）。用来给课表打「上次更新」的时间戳。 */
expect fun nowMillis(): Long

/** 当前本地时间的 `HH:mm`，用于「今日课程」的状态判断。 */
expect fun nowHHmm(): String
