package com.keylesspalace.tusky.components.timeline

import androidx.paging.testing.asSnapshot
import com.keylesspalace.tusky.components.preference.PreferencesFragment
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelineViewModel
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import retrofit2.Response

class NetworkTimelineViewModelTest {

    @Test
    fun `test load more when reading order is newest first`() {
        testLoadMore(PreferencesFragment.ReadingOrder.NEWEST_FIRST)
    }

    @Test
    fun `test load more when reading order is oldest first`() {
        testLoadMore(PreferencesFragment.ReadingOrder.OLDEST_FIRST)
    }

    fun testLoadMore(readingOrder: PreferencesFragment.ReadingOrder) = runTest {
        val api: MastodonApi = mock {
            onBlocking {
                publicTimeline(limit = TimelineViewModel.LOAD_AT_ONCE)
            }.doReturn(
                Response.success(
                    fakeStatusList(0, TimelineViewModel.LOAD_AT_ONCE),
                    Headers.headersOf("Link", "<https://tusky.test/api/v1/timelines/public?max_id=0>; rel=\"next\", <https://tusky.test/api/v1/timelines/public?min_id=29>; rel=\"prev\"")
                ),
                Response.success(
                    fakeStatusList(70, TimelineViewModel.LOAD_AT_ONCE),
                    Headers.headersOf("Link", "<https://tusky.test/api/v1/timelines/public?max_id=70>; rel=\"next\", <https://tusky.test/api/v1/timelines/public?min_id=99>; rel=\"prev\"")
                )
            )
            onBlocking {
                publicTimeline(maxId = "71", limit = TimelineViewModel.LOAD_AT_ONCE)
            }.doReturn(
                Response.success(
                    fakeStatusList(41, TimelineViewModel.LOAD_AT_ONCE),
                    Headers.headersOf("Link", "<https://tusky.test/api/v1/timelines/public?max_id=41>; rel=\"next\", <https://tusky.test/api/v1/timelines/public?min_id=70>; rel=\"prev\"")
                )
            )
            onBlocking {
                publicTimeline(maxId = "42", limit = TimelineViewModel.LOAD_AT_ONCE)
            }.doReturn(
                Response.success(
                    fakeStatusList(12, TimelineViewModel.LOAD_AT_ONCE),
                    Headers.headersOf("Link", "<https://tusky.test/api/v1/timelines/public?max_id=12>; rel=\"next\", <https://tusky.test/api/v1/timelines/public?min_id=41>; rel=\"prev\"")
                )
            )

            onBlocking {
                publicTimeline(minId = "29", limit = TimelineViewModel.LOAD_AT_ONCE)
            }.doReturn(
                Response.success(
                    fakeStatusList(30, TimelineViewModel.LOAD_AT_ONCE),
                    Headers.headersOf("Link", "<https://tusky.test/api/v1/timelines/public?max_id=30>; rel=\"next\", <https://tusky.test/api/v1/timelines/public?min_id=59>; rel=\"prev\"")
                )
            )
            onBlocking {
                publicTimeline(minId = "58", limit = TimelineViewModel.LOAD_AT_ONCE)
            }.doReturn(
                Response.success(
                    fakeStatusList(59, TimelineViewModel.LOAD_AT_ONCE),
                    Headers.headersOf("Link", "<https://tusky.test/api/v1/timelines/public?max_id=59>; rel=\"next\", <https://tusky.test/api/v1/timelines/public?min_id=88>; rel=\"prev\"")
                )
            )
        }

        val account = AccountEntity(
            id = 1,
            domain = "test.com",
            accessToken = "fakeToken",
            clientId = "fakeId",
            clientSecret = "fakeSecret",
            isActive = true
        )

        val viewmodel = NetworkTimelineViewModel(
            api = api,
            eventHub = mock {
                on { events } doReturn MutableSharedFlow()
            },
            accountManager = mock {
                on { accountsFlow } doReturn MutableStateFlow(listOf(account))
                on { activeAccount } doReturn account
                on { activeAccount(any()) } doReturn MutableStateFlow(account)
            },
            sharedPreferences = mock {
                on { getString(PrefKeys.READING_ORDER, null) } doReturn readingOrder.name.lowercase()
            }
        )

        viewmodel.init(TimelineViewModel.Kind.PUBLIC_FEDERATED, null, emptyList())

        // check that the initial load is correct
        val initialItems: List<StatusViewData> = viewmodel.statuses.asSnapshot {
            scrollTo(index = 0)
        }
        assertEquals(fakeStatusViewDataList(0, TimelineViewModel.LOAD_AT_ONCE), initialItems)

        // refresh to create a gap
        val itemsAfterRefresh: List<StatusViewData> = viewmodel.statuses.asSnapshot {
            refresh()
            scrollTo(index = 0)
        }
        assertEquals(
            fakeStatusViewDataList(71, TimelineViewModel.LOAD_AT_ONCE - 1) +
                StatusViewData.LoadMore("70", isLoading = false) +
                fakeStatusViewDataList(0, TimelineViewModel.LOAD_AT_ONCE),
            itemsAfterRefresh
        )

        // first load more - not enough to fill the gap
        viewmodel.loadMore("70")
        val itemsAfterFirstLoadMore: List<StatusViewData> = viewmodel.statuses.asSnapshot {
            scrollTo(29)
        }
        if (readingOrder == PreferencesFragment.ReadingOrder.NEWEST_FIRST) {
            assertEquals(
                fakeStatusViewDataList(42, TimelineViewModel.LOAD_AT_ONCE * 2 - 2) +
                    StatusViewData.LoadMore("41", isLoading = false) +
                    fakeStatusViewDataList(0, TimelineViewModel.LOAD_AT_ONCE),
                itemsAfterFirstLoadMore
            )

            // second load more - closes the gap
            viewmodel.loadMore("41")
            val itemsAfterSecondLoadMore: List<StatusViewData> = viewmodel.statuses.asSnapshot {
                scrollTo(58)
            }

            assertEquals(
                fakeStatusViewDataList(0, 100),
                itemsAfterSecondLoadMore
            )
        } else {
            assertEquals(
                fakeStatusViewDataList(71, TimelineViewModel.LOAD_AT_ONCE - 1) +
                    StatusViewData.LoadMore("59", isLoading = false) +
                    fakeStatusViewDataList(0, TimelineViewModel.LOAD_AT_ONCE * 2 - 1),
                itemsAfterFirstLoadMore
            )

            // second load more - closes the gap
            viewmodel.loadMore("59")
            val itemsAfterSecondLoadMore: List<StatusViewData> = viewmodel.statuses.asSnapshot {
                scrollTo(30)
            }

            assertEquals(
                fakeStatusViewDataList(0, 100),
                itemsAfterSecondLoadMore
            )
        }
    }

    private fun fakeStatusList(fromId: Int, count: Int): List<Status> {
        return List(count) { index ->
            fakeStatus((index + fromId).toString())
        }.asReversed()
    }

    private fun fakeStatusViewDataList(fromId: Int, count: Int): List<StatusViewData> {
        return List(count) { index ->
            fakeStatusViewData((index + fromId).toString())
        }.asReversed()
    }
}
