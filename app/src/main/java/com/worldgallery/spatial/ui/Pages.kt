package com.worldgallery.spatial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.SearchField
import com.pico.spatial.ui.design.Text
import com.worldgallery.spatial.app.GalleryViewModel
import com.worldgallery.spatial.app.Page
import com.worldgallery.spatial.data.ArtCategory
import com.worldgallery.spatial.data.Artwork
import com.worldgallery.spatial.data.Catalog
import com.worldgallery.spatial.data.Collection
import com.worldgallery.spatial.data.QueueSource
import com.worldgallery.spatial.data.tr

private val PagePadding = 24.dp

/** 01 · 名画首页: continue card + featured collections (Figma 8:807). */
@Composable
fun HomePage(vm: GalleryViewModel) {
    val session = vm.session
    val continueCollection = (session?.source as? QueueSource.FromCollection)?.let { Catalog.collection(it.collectionId) } ?: Catalog.collection("essentials")!!
    val continueArtwork = vm.current ?: Catalog.byId[continueCollection.artworkIds.first()]!!
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // "Continue collection" hero
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(WG.White).padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("继续观赏", "Continue viewing"), color = WG.Secondary, style = WGType.caption)
                Text(session?.source?.label() ?: continueCollection.title, color = WG.Ink, style = WGType.display)
                Text(
                    if (session != null) tr("从《${continueArtwork.titleZh}》继续", "Continue with ${continueArtwork.titleEn}")
                    else tr("从《${continueArtwork.titleZh}》开始", "Begin with ${continueArtwork.titleEn}"),
                    color = WG.Secondary, style = WGType.body,
                )
                PrimaryButton(if (session != null) tr("继续观赏", "Continue viewing") else tr("开始观赏", "Start viewing")) { vm.continueViewing() }
            }
            Box(Modifier.weight(1f).height(218.dp).galleryClickable { vm.continueViewing() }) {
                (continueArtwork.image?.path ?: continueArtwork.previewPath)?.let { AssetImage(it, Modifier.fillMaxSize()) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("精选合集", "Featured collections"), color = WG.Ink, style = WGType.title)
            Spacer(Modifier.weight(1f))
            GhostButton(tr("我的画框", "My frame")) { vm.openMyFrame() }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            for (c in Catalog.featured()) CollectionCard(c) { vm.navigate(Page.CollectionPage(c.id)) }
            val favCover = vm.favorites.firstOrNull()?.let { Catalog.byId[it] }
            ArtworkCard(
                title = tr("我的收藏", "Favorites"),
                meta = tr("喜欢的作品，随时继续", "Return to the works you love"),
                imagePath = favCover?.image?.thumbPath ?: favCover?.previewPath ?: "thumbs/bedroom.jpg",
            ) { vm.navigate(Page.Favorites) }
        }
    }
}

@Composable
fun CollectionCard(c: Collection, onClick: () -> Unit) {
    val cover = Catalog.byId[c.coverId]
    ArtworkCard(title = c.title, meta = c.subtitle, imagePath = cover?.image?.thumbPath ?: cover?.previewPath, onClick = onClick)
}

/** 02 · Collection: intro, grid, "在画框中循环" (Figma 8:882) — also used for the sculpture tab. */
@Composable
fun CollectionPage(vm: GalleryViewModel, id: String) {
    val c = Catalog.collection(id) ?: return
    val works = c.artworkIds.mapNotNull { Catalog.byId[it] }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (c.category == ArtCategory.PAINTING) tr("一幅一幅，慢慢看。", "One work. Your own pace.") else tr("让作品转过来。", "Let the work turn."), color = WG.Ink, style = WGType.display)
                Text(c.intro, color = WG.Secondary, style = WGType.body, modifier = Modifier.widthIn(max = 650.dp))
            }
            PrimaryButton(tr("开始观赏", "Start viewing")) { vm.startQueue(QueueSource.FromCollection(c.id), c.artworkIds) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            for (a in works) {
                ArtworkCard(title = a.title, meta = cardMeta(a), imagePath = a.image?.thumbPath ?: a.previewPath) { vm.openFromCollection(c.id, a.id) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (c.category == ArtCategory.PAINTING) PrimaryButton(tr("在画框中循环", "Play in frame")) { vm.loopInFrame(QueueSource.FromCollection(c.id)) }
            GhostButton(tr("查看我的收藏", "View favorites")) { vm.navigate(Page.Favorites) }
        }
        if (c.category == ArtCategory.SCULPTURE) {
            Text(
                tr("模型为博物馆复制件扫描，点击「加载 3D 模型」后按需下载；下载与解码状态分开显示。", "Models are museum cast scans, downloaded on demand after Load 3D; network progress and decoding are shown separately."),
                color = WG.Secondary, style = WGType.caption,
            )
        }
    }
}

fun cardMeta(a: Artwork): String = when (a.category) {
    ArtCategory.PAINTING -> a.artist.name.substringAfterLast('·').trim() + " · " + a.facts.date
    ArtCategory.SCULPTURE -> a.model?.let { tr("复制件扫描 · ", "Cast scan · ") + it.licenseLabel } ?: a.facts.date
}

/** 15 · 我的收藏 (Figma 8:1813). */
@Composable
fun FavoritesPage(vm: GalleryViewModel) {
    val works = vm.favorites.mapNotNull { Catalog.byId[it] }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (works.isEmpty()) {
            Text(tr("还没有收藏。观赏时点击 ♡ 就能把作品留在这里。", "No favorites yet. Tap ♡ while viewing to keep a work here."), color = WG.Secondary, style = WGType.body)
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                for (a in works) ArtworkCard(title = a.title, meta = cardMeta(a), imagePath = a.image?.thumbPath ?: a.previewPath) { vm.openFromFavorites(a.id) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PrimaryButton(tr("开始观赏", "Start viewing")) { vm.startQueue(QueueSource.Favorites, vm.favorites.toList()) }
                GhostButton(tr("在画框中循环", "Play in frame"), enabled = works.any { it.image != null }) { vm.loopInFrame(QueueSource.Favorites) }
            }
        }
    }
}

/** Search across Chinese / English titles, artists and museums (WG-02). */
@Composable
fun SearchPage(vm: GalleryViewModel) {
    var text by remember { mutableStateOf(vm.searchQuery) }
    val results = Catalog.search(vm.searchQuery)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SearchField(
            value = text,
            onValueChange = { text = it; vm.searchQuery = it },
            placeholder = { Text(tr("作品、作者或馆藏", "Work, artist or museum"), color = WG.Secondary, style = WGType.body) },
            onSearch = { vm.searchQuery = text },
        )
        if (vm.searchQuery.isBlank()) {
            Text(tr("支持中文译名、英文名、作者与馆藏。", "Search Chinese or English titles, artists and museums."), color = WG.Secondary, style = WGType.caption)
        } else if (results.isEmpty()) {
            Text(tr("没有找到匹配的作品。", "No matching works."), color = WG.Secondary, style = WGType.body)
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                for (a in results) ArtworkCard(title = a.title, meta = cardMeta(a), imagePath = a.image?.thumbPath ?: a.previewPath) { vm.openFromSearch(a.id) }
            }
            PrimaryButton(tr("从第一件开始观赏", "View results in order")) { vm.startQueue(QueueSource.Search(vm.searchQuery), results.map { it.id }) }
        }
    }
}
