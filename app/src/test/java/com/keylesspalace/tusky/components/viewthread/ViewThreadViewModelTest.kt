package com.keylesspalace.tusky.components.viewthread

import android.os.Looper.getMainLooper
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import at.connyduck.calladapter.networkresult.NetworkResult
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.StatusChangedEvent
import com.keylesspalace.tusky.components.timeline.fakeAccount
import com.keylesspalace.tusky.components.timeline.fakeStatus
import com.keylesspalace.tusky.components.timeline.fakeStatusViewData
import com.keylesspalace.tusky.components.timeline.toEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.Converters
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.di.NetworkModule
import com.keylesspalace.tusky.entity.StatusContext
import com.keylesspalace.tusky.network.MastodonApi
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(sdk = [34])
@RunWith(AndroidJUnit4::class)
class ViewThreadViewModelTest {

    private val api: MastodonApi = mock {
        onBlocking { getFilters() } doReturn NetworkResult.success(emptyList())
    }
    private val eventHub: EventHub = EventHub()

    private lateinit var viewModel: ViewThreadViewModel
    private lateinit var db: AppDatabase

    private val threadId = "2"
    private val moshi = NetworkModule.providesMoshi()

    /**
     * Execute each task synchronously.
     *
     * If you do not do this, and you have code like this under test:
     *
     * ```
     * fun someFunc() = viewModelScope.launch {
     *     _uiState.value = "initial value"
     *     // ...
     *     call_a_suspend_fun()
     *     // ...
     *     _uiState.value = "new value"
     * }
     * ```
     *
     * and a test like:
     *
     * ```
     * someFunc()
     * assertEquals("new value", viewModel.uiState.value)
     * ```
     *
     * The test will fail, because someFunc() yields at the `call_a_suspend_func()` point,
     * and control returns to the test before `_uiState.value` has been changed.
     */
    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    suspend fun setup(dbInit: suspend () -> Unit = {}) {
        shadowOf(getMainLooper()).idle()

        val accountManager: AccountManager = mock {
            on { activeAccount } doReturn AccountEntity(
                id = 1,
                domain = "mastodon.test",
                accessToken = "fakeToken",
                clientId = "fakeId",
                clientSecret = "fakeSecret",
                isActive = true
            )
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addTypeConverter(Converters(moshi))
            .allowMainThreadQueries()
            .build()

        dbInit()

        viewModel = ViewThreadViewModel(api, db, eventHub, accountManager, threadId)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `should emit status and context when both load`() = runTest {
        mockSuccessResponses()

        setup()

        assertEquals(
            ThreadUiState.Success(
                statusViewData = listOf(
                    fakeStatusViewData(id = "1", spoilerText = "Test"),
                    fakeStatusViewData(id = threadId, inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test"),
                    fakeStatusViewData(id = "3", inReplyToId = threadId, inReplyToAccountId = "1", spoilerText = "Test")
                ),
                isRefreshing = false,
                isloadingThread = false,
                revealButton = RevealButtonState.REVEAL
            ),
            viewModel.uiState.first()
        )
    }

    @Test
    fun `should emit status even if context fails to load`() = runTest {
        api.stub {
            onBlocking { status(threadId) } doReturn NetworkResult.success(fakeStatus(id = threadId, inReplyToId = "1", inReplyToAccountId = "1"))
            onBlocking { statusContext(threadId) } doReturn NetworkResult.failure(IOException())
        }

        setup()

        assertEquals(
            ThreadUiState.Success(
                statusViewData = listOf(
                    fakeStatusViewData(id = threadId, inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true)
                ),
                isRefreshing = false,
                isloadingThread = false,
                revealButton = RevealButtonState.NO_BUTTON
            ),
            viewModel.uiState.first()
        )
    }

    @Test
    fun `should emit error when status and context fail to load`() = runTest {
        api.stub {
            onBlocking { status(threadId) } doReturn NetworkResult.failure(IOException())
            onBlocking { statusContext(threadId) } doReturn NetworkResult.failure(IOException())
        }

        setup()

        assertEquals(
            ThreadUiState.Error::class.java,
            viewModel.uiState.first().javaClass
        )
    }

    @Test
    fun `should emit error when status fails to load`() = runTest {
        api.stub {
            onBlocking { status(threadId) } doReturn NetworkResult.failure(IOException())
            onBlocking { statusContext(threadId) } doReturn NetworkResult.success(
                StatusContext(
                    ancestors = listOf(fakeStatus(id = "1")),
                    descendants = listOf(fakeStatus(id = "3", inReplyToId = threadId, inReplyToAccountId = "1"))
                )
            )
        }

        setup()

        assertEquals(
            ThreadUiState.Error::class.java,
            viewModel.uiState.first().javaClass
        )
    }

    @Test
    fun `should update state when reveal button is toggled`() = runTest {
        mockSuccessResponses()

        setup()
        viewModel.toggleRevealButton()

        assertEquals(
            ThreadUiState.Success(
                statusViewData = listOf(
                    fakeStatusViewData(id = "1", spoilerText = "Test", isExpanded = true),
                    fakeStatusViewData(
                        id = threadId,
                        inReplyToId = "1",
                        inReplyToAccountId = "1",
                        isDetailed = true,
                        spoilerText = "Test",
                        isExpanded = true
                    ),
                    fakeStatusViewData(id = "3", inReplyToId = threadId, inReplyToAccountId = "1", spoilerText = "Test", isExpanded = true)
                ),
                isRefreshing = false,
                isloadingThread = false,
                revealButton = RevealButtonState.HIDE
            ),
            viewModel.uiState.first()
        )
    }

    @Test
    fun `should handle status changed event`() = runTest {
        mockSuccessResponses()

        setup()

        eventHub.dispatch(StatusChangedEvent(fakeStatus(id = "1", spoilerText = "Test", favourited = false)))

        assertEquals(
            ThreadUiState.Success(
                statusViewData = listOf(
                    fakeStatusViewData(id = "1", spoilerText = "Test", favourited = false),
                    fakeStatusViewData(id = threadId, inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test"),
                    fakeStatusViewData(id = "3", inReplyToId = threadId, inReplyToAccountId = "1", spoilerText = "Test")
                ),
                isRefreshing = false,
                isloadingThread = false,
                revealButton = RevealButtonState.REVEAL
            ),
            viewModel.uiState.first()
        )
    }

    @Test
    fun `should remove status`() = runTest {
        mockSuccessResponses()

        setup()

        viewModel.removeStatus("3")

        assertEquals(
            ThreadUiState.Success(
                statusViewData = listOf(
                    fakeStatusViewData(id = "1", spoilerText = "Test"),
                    fakeStatusViewData(id = threadId, inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test")
                ),
                isRefreshing = false,
                isloadingThread = false,
                revealButton = RevealButtonState.REVEAL
            ),
            viewModel.uiState.first()
        )
    }

    @Test
    fun `should change status expanded state`() = runTest {
        mockSuccessResponses()

        setup()

        viewModel.changeExpanded(
            true,
            fakeStatusViewData(id = threadId, inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test")
        )

        assertEquals(
            ThreadUiState.Success(
                statusViewData = listOf(
                    fakeStatusViewData(id = "1", spoilerText = "Test"),
                    fakeStatusViewData(
                        id = threadId,
                        inReplyToId = "1",
                        inReplyToAccountId = "1",
                        isDetailed = true,
                        spoilerText = "Test",
                        isExpanded = true
                    ),
                    fakeStatusViewData(id = "3", inReplyToId = threadId, inReplyToAccountId = "1", spoilerText = "Test")
                ),
                isRefreshing = false,
                isloadingThread = false,
                revealButton = RevealButtonState.REVEAL
            ),
            viewModel.uiState.first()
        )
    }

    @Test
    fun `should change content collapsed state`() = runTest {
        mockSuccessResponses()

        setup()

        viewModel.changeContentCollapsed(
            true,
            fakeStatusViewData(id = threadId, inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test")
        )

        assertEquals(
            ThreadUiState.Success(
                statusViewData = listOf(
                    fakeStatusViewData(id = "1", spoilerText = "Test"),
                    fakeStatusViewData(
                        id = threadId,
                        inReplyToId = "1",
                        inReplyToAccountId = "1",
                        isDetailed = true,
                        spoilerText = "Test",
                        isCollapsed = true
                    ),
                    fakeStatusViewData(id = "3", inReplyToId = threadId, inReplyToAccountId = "1", spoilerText = "Test")
                ),
                isRefreshing = false,
                isloadingThread = false,
                revealButton = RevealButtonState.REVEAL
            ),
            viewModel.uiState.first()
        )
    }

    @Test
    fun `should change content showing state`() = runTest {
        mockSuccessResponses()

        setup()

        viewModel.changeContentShowing(
            true,
            fakeStatusViewData(id = threadId, inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test")
        )

        assertEquals(
            ThreadUiState.Success(
                statusViewData = listOf(
                    fakeStatusViewData(id = "1", spoilerText = "Test"),
                    fakeStatusViewData(
                        id = threadId,
                        inReplyToId = "1",
                        inReplyToAccountId = "1",
                        isDetailed = true,
                        spoilerText = "Test",
                        isShowingContent = true
                    ),
                    fakeStatusViewData(id = "3", inReplyToId = threadId, inReplyToAccountId = "1", spoilerText = "Test")
                ),
                isRefreshing = false,
                isloadingThread = false,
                revealButton = RevealButtonState.REVEAL
            ),
            viewModel.uiState.first()
        )
    }

    @Test
    fun `should not change expanded state when status is refreshed`() = runTest {
        mockSuccessResponses()

        setup()

        viewModel.changeExpanded(
            true,
            fakeStatusViewData(id = threadId, inReplyToId = "1", inReplyToAccountId = "1", isDetailed = true, spoilerText = "Test")
        )

        viewModel.refresh()

        assertEquals(
            ThreadUiState.Success(
                statusViewData = listOf(
                    fakeStatusViewData(id = "1", spoilerText = "Test"),
                    fakeStatusViewData(
                        id = threadId,
                        inReplyToId = "1",
                        inReplyToAccountId = "1",
                        isDetailed = true,
                        spoilerText = "Test",
                        isExpanded = true
                    ),
                    fakeStatusViewData(id = "3", inReplyToId = threadId, inReplyToAccountId = "1", spoilerText = "Test")
                ),
                isRefreshing = false,
                isloadingThread = false,
                revealButton = RevealButtonState.REVEAL
            ),
            viewModel.uiState.first()
        )
    }

    @Test
    fun `should not change collapsed state of cached status on load`() = runTest {
        mockSuccessResponses()

        setup {
            db.timelineAccountDao().insert(fakeAccount().toEntity(1))
            db.timelineStatusDao().insert(
                fakeStatus(id = threadId).toEntity(1, expanded = true, contentShowing = true, contentCollapsed = false, filterActive = true)
            )
        }

        assertEquals(
            ThreadUiState.Success(
                statusViewData = listOf(
                    fakeStatusViewData(id = "1", spoilerText = "Test"),
                    fakeStatusViewData(
                        id = threadId,
                        inReplyToId = "1",
                        inReplyToAccountId = "1",
                        isDetailed = true,
                        spoilerText = "Test",
                        isExpanded = true,
                        isShowingContent = true
                    ),
                    fakeStatusViewData(id = "3", inReplyToId = threadId, inReplyToAccountId = "1", spoilerText = "Test")
                ),
                isRefreshing = false,
                isloadingThread = false,
                revealButton = RevealButtonState.REVEAL
            ),
            viewModel.uiState.first()
        )
    }

    private fun mockSuccessResponses() {
        api.stub {
            onBlocking {
                status(threadId)
            } doReturn NetworkResult.success(fakeStatus(id = threadId, inReplyToId = "1", inReplyToAccountId = "1", spoilerText = "Test"))
            onBlocking { statusContext(threadId) } doReturn NetworkResult.success(
                StatusContext(
                    ancestors = listOf(fakeStatus(id = "1", spoilerText = "Test")),
                    descendants = listOf(fakeStatus(id = "3", inReplyToId = threadId, inReplyToAccountId = "1", spoilerText = "Test"))
                )
            )
        }
    }
}
