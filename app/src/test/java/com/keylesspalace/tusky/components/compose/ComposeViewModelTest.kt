package com.keylesspalace.tusky.components.compose

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.entity.NewPoll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.settings.DefaultReplyVisibility
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config

@Config(sdk = [34])
@RunWith(AndroidJUnit4::class)
class ComposeViewModelTest {

    private lateinit var api: MastodonApi
    private lateinit var accountManager: AccountManager
    private lateinit var eventHub: EventHub
    private lateinit var viewModel: ComposeViewModel

    private fun setup(
        defaultReplyVisibility: DefaultReplyVisibility = DefaultReplyVisibility.UNLISTED,
        options: ComposeActivity.ComposeOptions?,
        savedState: SavedStateHandle = SavedStateHandle()
    ) {
        api = mock()
        accountManager = mock {
            on { activeAccount } doReturn
                AccountEntity(
                    id = 1,
                    domain = "test.domain",
                    accessToken = "fakeToken",
                    clientId = "fakeId",
                    clientSecret = "fakeSecret",
                    isActive = true,
                    defaultReplyPrivacy = defaultReplyVisibility
                )
        }
        eventHub = EventHub()

        viewModel = ComposeViewModel(
            api = api,
            accountManager = accountManager,
            mediaUploader = mock(),
            serviceClient = mock(),
            draftHelper = mock(),
            state = savedState,
            composeOptions = options,
            instanceInfoRepo = mock(),
        )
    }

    @Test
    fun `startingVisibility initially set to defaultPostPrivacy for post`() {
        setup(options = null)

        assertEquals(Status.Visibility.PUBLIC, viewModel.statusVisibility.value)
    }

    @Test
    fun `startingVisibility initially set to replyPostPrivacy for reply`() {
        setup(options = ComposeActivity.ComposeOptions(inReplyToId = "123"))

        assertEquals(Status.Visibility.UNLISTED, viewModel.statusVisibility.value)
    }

    @Test
    fun `startingVisibility initially set to defaultPostPrivacy when replyPostPrivacy is MATCH_DEFAULT_POST_VISIBILITY for reply`() {
        setup(
            defaultReplyVisibility = DefaultReplyVisibility.MATCH_DEFAULT_POST_VISIBILITY,
            options = ComposeActivity.ComposeOptions(inReplyToId = "123")
        )

        assertEquals(Status.Visibility.PUBLIC, viewModel.statusVisibility.value)
    }

    @Test
    fun `saved state takes precedence over compose options`() {
        val oldPoll = NewPoll(
            options = listOf("Always", "Never"),
            expiresIn = 10000,
            multiple = false
        )
        val updatedPoll = NewPoll(
            options = listOf("Yes", "No"),
            expiresIn = 1337,
            multiple = false
        )
        setup(
            defaultReplyVisibility = DefaultReplyVisibility.MATCH_DEFAULT_POST_VISIBILITY,
            options = ComposeActivity.ComposeOptions(
                visibility = Status.Visibility.UNLISTED,
                sensitive = true,
                contentWarning = "CW Test",
                poll = oldPoll
            ),
            SavedStateHandle(
                mapOf(
                    "STATUS_VISIBILITY" to Status.Visibility.PRIVATE,
                    "SHOW_CONTENT_WARNING" to false,
                    "MARK_MEDIA_AS_SENSITIVE" to false,
                    "POLL" to updatedPoll
                )
            )
        )

        assertEquals(Status.Visibility.PRIVATE, viewModel.statusVisibility.value)
        assertEquals(false, viewModel.showContentWarning.value)
        assertEquals(false, viewModel.markMediaAsSensitive.value)
        assertEquals(updatedPoll, viewModel.poll.value)
    }
}
