package io.github.showcode1024.oneframe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.showcode1024.oneframe.ui.OneFrameTheme
import io.github.showcode1024.oneframe.ui.UpdateDialogContent
import io.github.showcode1024.oneframe.update.UpdateRelease
import io.github.showcode1024.oneframe.update.UpdateState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun longReleaseNotesKeepDownloadActionVisible() {
        var clicked = false
        val release = UpdateRelease("v0.1.7-alpha", "0.1.7-alpha", ("## 更新\n- 新功能说明\n").repeat(100),
            "https://github.com/showcode1024/OneFrame/releases/download/v0.1.7-alpha/OneFrame-0.1.7-alpha.apk", 1_048_576, "a".repeat(64))
        compose.setContent { OneFrameTheme { UpdateDialogContent(UpdateState(dialog = true, release = release), {}, {}, { clicked = true }, {}) } }
        compose.onNodeWithText("浏览器下载").assertIsDisplayed().performClick()
        assertTrue(clicked)
        compose.onNodeWithText("版本说明").assertIsDisplayed()
    }
}
