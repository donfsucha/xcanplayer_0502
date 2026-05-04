package com.cnanfc.xcanplayer

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.*

class ScheduleUiController(
    private val context: Context,
    private val localStore: LocalStore,
    private val onScheduleUpdated: (List<ScheduleItem>) -> Unit
) {
    fun showScheduleDialog(scheduleList: MutableList<ScheduleItem>, onDismiss: () -> Unit) {
        val builder = AlertDialog.Builder(context)
        val scrollView = ScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
            setBackgroundColor(Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        layout.addView(TextView(context).apply {
            text = "스케줄 관리"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
        })
        layout.addView(createSpace(30))

        val presetLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val inputTitle = EditText(context).apply {
            hint = "제목 입력"
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
        }

        val inputUrl = EditText(context).apply {
            hint = "URL 붙여넣기"
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
        }

        val btnTimeSelect = Button(context).apply {
            text = "시간 선택 (터치)"
            setBackgroundColor(Color.parseColor("#EEEEEE"))
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.5f
            )
        }

        var selectedH = -1
        var selectedM = -1

        presetLayout.addView(createStyledButton("🌿 생명의삶(6시)", "#66BB6A") {
            inputTitle.setText("생명의삶")
            inputUrl.setText("https://www.fondant.kr/series/00090200-0000-0000-0000-00000000071b?category=episode")
            selectedH = 6
            selectedM = 0
            btnTimeSelect.text = "06:00"
        })

        presetLayout.addView(createSpaceHorizontal(15))

        presetLayout.addView(createStyledButton("📖 성경통독(8시)", "#42A5F5") {
            inputTitle.setText("성경통독")
            inputUrl.setText("https://www.fondant.kr/series/00090228-5db3-dc44-3c29-52bcaf0002ce?category=episode")
            selectedH = 8
            selectedM = 0
            btnTimeSelect.text = "08:00"
        })

        layout.addView(presetLayout)
        layout.addView(createSpace(30))
        layout.addView(inputTitle)
        layout.addView(createSpace(15))
        layout.addView(inputUrl)
        layout.addView(createSpace(20))

        btnTimeSelect.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(context, { _, h, m ->
                selectedH = h
                selectedM = m
                btnTimeSelect.text = String.format(Locale.getDefault(), "%02d:%02d", h, m)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
        }

        val btnAdd = Button(context).apply {
            text = "➕ 추가"
            setBackgroundColor(Color.parseColor("#2979FF"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(20, 0, 0, 0)
            }
        }

        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        fun refreshList() {
            listContainer.removeAllViews()

            if (scheduleList.isEmpty()) {
                listContainer.addView(TextView(context).apply {
                    text = "등록된 스케줄이 없습니다."
                    setTextColor(Color.GRAY)
                    gravity = Gravity.CENTER
                    setPadding(0, 30, 0, 30)
                })
            } else {
                scheduleList.forEachIndexed { index, item ->
                    val itemRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 15, 0, 15)
                        gravity = Gravity.CENTER_VERTICAL
                        setBackgroundColor(Color.parseColor("#FAFAFA"))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }

                    itemRow.addView(TextView(context).apply {
                        text = String.format(
                            Locale.getDefault(),
                            "⏰ %02d:%02d | %s",
                            item.hour,
                            item.minute,
                            item.title
                        )
                        setTextColor(Color.BLACK)
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    })

                    itemRow.addView(Button(context).apply {
                        text = "삭제"
                        textSize = 13f
                        setTextColor(Color.RED)
                        setBackgroundColor(Color.parseColor("#FFEBEE"))
                        setPadding(20, 0, 20, 0)
                        setOnClickListener {
                            scheduleList.removeAt(index)
                            localStore.saveSchedule(scheduleList)
                            onScheduleUpdated(scheduleList.toList())
                            refreshList()
                        }
                    })

                    listContainer.addView(itemRow)
                    listContainer.addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            1
                        )
                        setBackgroundColor(Color.LTGRAY)
                    })
                }
            }
        }

        btnAdd.setOnClickListener {
            if (selectedH == -1 || inputTitle.text.isEmpty() || inputUrl.text.isEmpty()) {
                Toast.makeText(context, "시간, 제목, URL을 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            scheduleList.add(
                ScheduleItem(
                    selectedH,
                    selectedM,
                    inputTitle.text.toString(),
                    inputUrl.text.toString()
                )
            )
            scheduleList.sortBy { it.hour * 60 + it.minute }
            localStore.saveSchedule(scheduleList)
            onScheduleUpdated(scheduleList.toList())

            inputTitle.setText("")
            inputUrl.setText("")
            btnTimeSelect.text = "시간 선택 (터치)"
            selectedH = -1
            selectedM = -1

            refreshList()
            Toast.makeText(context, "추가되었습니다.", Toast.LENGTH_SHORT).show()
        }

        layout.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btnTimeSelect)
            addView(btnAdd)
        })

        layout.addView(createSpace(30))
        layout.addView(TextView(context).apply {
            text = "▼ 스케줄 목록"
            setTypeface(null, Typeface.BOLD)
            textSize = 16f
            setTextColor(Color.DKGRAY)
        })
        layout.addView(createSpace(10))
        layout.addView(listContainer)

        refreshList()

        scrollView.addView(layout)
        builder.setView(scrollView)
        builder.setPositiveButton("닫기", null)

        val dialog = builder.create()
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
    }

    private fun createStyledButton(text: String, colorHex: String, onClick: (View) -> Unit): Button {
        return Button(context).apply {
            this.text = text
            setBackgroundColor(Color.parseColor(colorHex))
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            setPadding(5, 0, 5, 0)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
            ).apply {
                setMargins(5, 0, 5, 0)
            }
            setOnClickListener(onClick)
        }
    }

    private fun createSpace(height: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height
        )
    }

    private fun createSpaceHorizontal(width: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            width,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}