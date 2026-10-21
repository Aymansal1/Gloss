package app.gloss

import android.app.Application
import app.gloss.data.CatalogRepository
import app.gloss.data.LibraryStore
import app.gloss.data.ReadingState
import app.gloss.download.DownloadCenter
import app.gloss.download.SourceFetcher
import app.gloss.pdf.PdfPages
import app.gloss.search.SearchIndex
import app.gloss.update.UpdateChecker
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class AppContainer(app: Application) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "Gloss/${BuildConfig.VERSION_NAME} (Android; +https://github.com/Aymansal1/Gloss)").build())
        }
        .build()

    val catalog = CatalogRepository(http, File(app.filesDir, "catalog.json"))
    val library = LibraryStore(File(app.filesDir, "library"))
    val search = SearchIndex(app, library)
    val reading = ReadingState(app)
    val downloads = DownloadCenter(app)
    val fetcher = SourceFetcher(http)
    val pdf = PdfPages(app)
    val updates = UpdateChecker(http, BuildConfig.VERSION_NAME)
}

class GlossApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        container = AppContainer(this)
        appScope.launch {
            container.library.scan()
            container.search.sync()
        }
        appScope.launch { container.catalog.load() }
        appScope.launch { container.updates.check() }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.http })) }
            .crossfade(true)
            .build()
}
