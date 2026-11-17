package com.jianke.schedule.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jianke.schedule.shared.model.Schedule
import com.jianke.schedule.shared.model.layoutDay
import com.jianke.schedule.shared.model.schoolBigPeriods
import com.jianke.schedule.shared.parse.ScheduleWeek
import com.jianke.schedule.shared.platform.todayIso
import com.jianke.schedule.shared.util.IsoDate

private val HEADER_HEIGHT = 42.dp
private val GUTTER_WIDTH = 42.dp
private val WEEK_DAYS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 行高下限：再小就看不清了；正常情况下会按屏幕高度均分。 */
private val MIN_ROW_HEIGHT = 44.dp

/**
 * 周课表：左边节次栏，右边 7 天；同一天的课按 [layoutDay] 的车道并排。
 *
 * 行高不写死——用 [BoxWithConstraints] 拿到剩余高度后按 5 个大节均分，
 * 这样整张表**铺满屏幕**，也不会在矮屏上把内容顶出去。
 */
@Composable
fun WeekScreen(schedule: Schedule) {
    val today = todayIso()
    val totalWeeks = maxOf(1, schedule.semester.totalWeeks)
    // 「当前周」和「正在看的周」要分开：只有翻回当前周时，今天那列才高亮
    val currentWeek = remember(schedule, today) {
        ScheduleWeek.currentWeekOf(schedule, today).coerceIn(1, totalWeeks)
    }
    val todayWeekday = remember(today) { IsoDate.weekdayIndex(today) }
    var week by remember(schedule) { mutableIntStateOf(currentWeek) }

    Column(modifier = Modifier.fillMaxSize()) {
        WeekNavBar(
            week = week,
            total = totalWeeks,
            onPrev = { if (week > 1) week -= 1 },
            onNext = { if (week < totalWeeks) week += 1 },
        )

        val dates = ScheduleWeek.weekDatesOf(schedule.semester.startDate, week)
        val courses = schedule.coursesOfWeek(week)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 6.dp),
        ) {
            // 剩余高度按 5 个大节均分；矮屏时退到最小行高，整表可滚动前先保证可读
            val rowHeight = ((maxHeight - HEADER_HEIGHT) / schoolBigPeriods.size)
                .coerceAtLeast(MIN_ROW_HEIGHT)

            Row(modifier = Modifier.fillMaxSize()) {
                // 节次栏：节次 + 起止时间
                // （8:20-10:00 / 10:20-12:00 / 14:00-15:40 / 16:00-17:40 / 18:40-20:00）
                Column(modifier = Modifier.width(GUTTER_WIDTH)) {
                    Spacer(Modifier.height(HEADER_HEIGHT))
                    schoolBigPeriods.forEach { p ->
                        Column(
                            modifier = Modifier
                                .height(rowHeight)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "${p.startPeriod}-${p.endPeriod}",
                                style = Type.tiny,
                                color = LocalPalette.current.subText,
                            )
                            Text(
                                text = p.startTime,
                                style = Type.micro,
                                color = LocalPalette.current.subText.copy(alpha = 0.75f),
                            )
                            Text(
                                text = p.endTime,
                                style = Type.micro,
                                color = LocalPalette.current.subText.copy(alpha = 0.75f),
                            )
                        }
                    }
                }

                for (day in 1..7) {
                    Column(modifier = Modifier.weight(1f)) {
                        DayHeader(
                            index = day - 1,
                            date = dates.getOrNull(day - 1),
                            isToday = week == currentWeek && day == todayWeekday,
                        )
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight * schoolBigPeriods.size),
                        ) {
                            val cellWidth: Dp = maxWidth
                            GridLines(rowHeight)
                            val placed = layoutDay(courses.filter { it.dayOfWeek == day })
                            placed.forEach { p ->
                                val color = if (p.course.color != 0) {
                                    Color(p.course.color)
                                } else {
                                    LocalPalette.current.primary
                                }
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = cellWidth * p.lane / p.lanes,
                                            y = rowHeight * p.startRow,
                                        )
                                        .width(cellWidth / p.lanes)
                                        .height(rowHeight * (p.endRow - p.startRow + 1))
                                        .padding(1.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(color)
                                        .padding(horizontal = 3.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = p.course.name,
                                        style = Type.tiny,
                                        color = Color.White,
                                        maxLines = 4,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekNavBar(week: Int, total: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹",
            style = Type.title,
            color = if (week > 1) palette.primary else palette.divider,
            modifier = Modifier.clickable(enabled = week > 1, onClick = onPrev).padding(horizontal = 10.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(text = "第 $week 周 / 共 $total 周", style = Type.headline, align = TextAlign.Center)
        }
        Text(
            text = "›",
            style = Type.title,
            color = if (week < total) palette.primary else palette.divider,
            modifier = Modifier.clickable(enabled = week < total, onClick = onNext).padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun DayHeader(index: Int, date: String?, isToday: Boolean) {
    val palette = LocalPalette.current
    // 今天那一列：文字加蓝色底色，扫一眼就知道自己在哪一天
    val bg = if (isToday) palette.primary.copy(alpha = 0.16f) else Color.Transparent
    val fg = if (isToday) palette.primary else palette.subText
    Column(
        modifier = Modifier
            .height(HEADER_HEIGHT)
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = WEEK_DAYS[index], style = Type.tiny, color = fg)
        Text(
            text = date?.substringAfterLast('-')?.trimStart('0') ?: "",
            style = Type.micro,
            color = fg.copy(alpha = if (isToday) 0.9f else 0.7f),
        )
    }
}

/** 每个大节顶部一条细线：让空格子也能看出节次边界。 */
@Composable
private fun GridLines(rowHeight: Dp) {
    val palette = LocalPalette.current
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(schoolBigPeriods.size) {
            Box(modifier = Modifier.fillMaxWidth().height(rowHeight)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .align(Alignment.TopCenter)
                        .background(palette.divider),
                )
            }
        }
    }
}
