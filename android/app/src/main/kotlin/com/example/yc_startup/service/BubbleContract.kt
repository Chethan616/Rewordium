package com.example.yc_startup.service

sealed class MenuAction {
    object Translate : MenuAction()
    object Summarize : MenuAction()
    object GrammarCheck : MenuAction()
}

// The UI only needs to perform an action. It doesn't need to get text.
interface BubbleListener {
    fun onPerformAction(action: MenuAction)
}