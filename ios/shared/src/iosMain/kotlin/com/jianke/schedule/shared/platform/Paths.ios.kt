package com.jianke.schedule.shared.platform

import platform.Foundation.NSHomeDirectory

actual fun documentsDir(): String = NSHomeDirectory() + "/Documents"
