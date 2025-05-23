package com.grindrplus.hooks

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.children
import com.grindrplus.BuildConfig
import com.grindrplus.GrindrPlus
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.getObjectField

class ModSettings : Hook(
    "Mod settings",
    "GrindrPlus settings"
) {
    private val settingsActivity = "com.grindrapp.android.ui.settings.SettingsActivity"
    private val settingsFragment = "com.grindrplus.ui.fragments.SettingsFragment"

    override fun init() {
        val nestedScrollViewClass = findClass("androidx.core.widget.NestedScrollView")
        val getChildAtNestedScrollView = nestedScrollViewClass.getMethod(
            "getChildAt",
            Int::class.java
        )

        val fragmentClass = findClass("androidx.fragment.app.Fragment")
        val fragmentActivityClass = findClass("androidx.fragment.app.FragmentActivity")
        val getSupportFragmentManager = fragmentActivityClass.getMethod("getSupportFragmentManager")
        val fragmentManagerClass = findClass("androidx.fragment.app.FragmentManager")
        val beginTransaction = fragmentManagerClass.getMethod("beginTransaction")
        val fragmentTransactionClass =
            findClass("androidx.fragment.app.FragmentTransaction")
        val addFragmentTransaction =
            fragmentTransactionClass.getMethod("add", Int::class.java, fragmentClass)
        val commitFragmentTransaction = fragmentTransactionClass.getMethod("commit")

        findClass(settingsActivity)
            .hook("onCreate", HookStage.AFTER) { param ->
                val activity = param.thisObject() as Activity

                val settingsViewBindingLazy = getObjectField(param.thisObject(), "d0") // good luck
                val settingsViewBinding = settingsViewBindingLazy::class
                    .java.getMethod("getValue").invoke(settingsViewBindingLazy)

                var settingsRoot =
                    param.thisObject()::class.java.getMethod("E").invoke(param.thisObject()) // good luck
                settingsRoot::class.java.declaredFields.reversed().forEach { field ->
                    field.isAccessible = true
                    val fieldValue = field.get(settingsRoot)

                    if (fieldValue is TextView && fieldValue.text.isEmpty()) {
                        fieldValue.setOnClickListener {
                            GrindrPlus.showToast(
                                Toast.LENGTH_LONG,
                                "GrindrPlus v${BuildConfig.VERSION_NAME}"
                            )
                        }
                        return@forEach
                    }
                }

                val settingsViewBindingRoot = settingsViewBinding::class
                    .java.getMethod("getRoot").invoke(settingsViewBinding) as RelativeLayout
                val settingsNestedScrollView = settingsViewBindingRoot.getChildAt(1)

                if (nestedScrollViewClass.isInstance(settingsNestedScrollView)) {
                    val settingsScrollingContentLayout = getChildAtNestedScrollView
                        .invoke(settingsNestedScrollView, 0) as LinearLayout
                    val settingsExampleContainer = settingsScrollingContentLayout
                        .getChildAt(1) as LinearLayout
                    val settingsExampleHeader = settingsExampleContainer.getChildAt(0) as TextView

                    val settingsExampleSubContainer =
                        settingsExampleContainer.children.first { it is LinearLayout } as LinearLayout
                    val settingsExampleSubContainerTextView =
                        settingsExampleSubContainer.getChildAt(0) as TextView

                    val modContainer = LinearLayout(activity).apply {
                        layoutParams = settingsExampleContainer.layoutParams
                        orientation = settingsExampleContainer.orientation
                        setPadding(
                            settingsExampleContainer.paddingLeft,
                            settingsExampleContainer.paddingTop,
                            settingsExampleContainer.paddingRight,
                            settingsExampleContainer.paddingBottom
                        )
                        id = View.generateViewId()
                        textAlignment = settingsExampleContainer.textAlignment
                    }

                    val modHeader = TextView(activity).apply {
                        text = "GrindrPlus"
                        layoutParams = settingsExampleHeader.layoutParams
                        setPadding(
                            settingsExampleHeader.paddingLeft, settingsExampleHeader.paddingTop,
                            settingsExampleHeader.paddingRight, settingsExampleHeader.paddingBottom
                        )
                        textSize = 14f
                        isAllCaps = true
                        setTypeface(
                            settingsExampleHeader.typeface,
                            settingsExampleHeader.typeface.style
                        )
                        setTextColor(settingsExampleHeader.currentTextColor)
                    }

                    val modSubContainer = LinearLayout(activity).apply {
                        layoutParams = settingsExampleSubContainer.layoutParams
                        orientation = settingsExampleSubContainer.orientation
                        setPadding(
                            settingsExampleSubContainer.paddingLeft,
                            settingsExampleSubContainer.paddingTop,
                            settingsExampleSubContainer.paddingRight,
                            settingsExampleSubContainer.paddingBottom
                        )
                        id = View.generateViewId()
                        textAlignment = settingsExampleSubContainer.textAlignment
                    }

                    val modSubContainerTextView = TextView(activity).apply {
                        text = "Mod Settings"
                        layoutParams = settingsExampleSubContainerTextView.layoutParams
                        setPadding(
                            settingsExampleSubContainerTextView.paddingLeft,
                            settingsExampleSubContainerTextView.paddingTop,
                            settingsExampleSubContainerTextView.paddingRight,
                            settingsExampleSubContainerTextView.paddingBottom
                        )
                        textSize = 16f
                        setTypeface(
                            settingsExampleSubContainerTextView.typeface,
                            settingsExampleSubContainerTextView.typeface.style
                        )
                        setTextColor(settingsExampleSubContainerTextView.currentTextColor)
                        visibility = View.VISIBLE
                    }

                    modSubContainer.setOnClickListener {
                        val hooksFragmentInstance =
                            findClass(settingsFragment).constructors.first().newInstance()
                        val supportFragmentManager = getSupportFragmentManager.invoke(activity)
                        val fragmentTransaction = beginTransaction.invoke(supportFragmentManager)
                        addFragmentTransaction.invoke(
                            fragmentTransaction,
                            android.R.id.content,
                            hooksFragmentInstance
                        )
                        commitFragmentTransaction.invoke(fragmentTransaction)
                    }

                    modSubContainer.addView(modSubContainerTextView, 0)
                    modContainer.addView(modHeader, 0)
                    modContainer.addView(modSubContainer, 1)
                    settingsScrollingContentLayout.addView(modContainer, 0)
                }
            }
    }
}