package com.worldgallery.spatial.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Text
import com.worldgallery.spatial.app.GalleryViewModel
import com.worldgallery.spatial.app.InfoBinding
import com.worldgallery.spatial.data.Artwork
import com.worldgallery.spatial.data.Catalog
import com.worldgallery.spatial.data.QueueSource
import com.worldgallery.spatial.data.tr

/**
 * 06 · 作品介绍 (Figma 8:1126 "Artwork Info / Sub Window"). First screen: titles, artist, date, museum,
 * short text; then medium, size, background and the separate image / text licences (PRD 5.5).
 */
@Composable
fun InfoContent(vm: GalleryViewModel, a: Artwork, binding: InfoBinding) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    LaunchedEffect(vm.infoScrollKey, a.id) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("作品介绍", "About this work"), color = WG.Ink, style = WGType.title)
                if (binding == InfoBinding.FRAME && vm.frame.mode == com.worldgallery.spatial.data.FrameMode.LOOP) {
                    Text(tr("阅读期间暂停换画", "Slideshow paused while reading"), color = WG.Secondary, style = WGType.caption)
                }
            }
            ControlButton(WGIcon.CLOSE, tr("关闭", "Close")) { vm.closeInfo() }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(a.title, color = WG.Ink, style = WGType.headline)
            Divider()
            Text(a.artist.name, color = WG.Ink, style = WGType.title)
            Text(a.artist.dates, color = WG.Secondary, style = WGType.caption)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FactRow(tr("创作年代", "Date"), a.facts.date)
                FactRow(tr("媒介", "Medium"), a.facts.medium)
                a.facts.dimensions?.let { FactRow(tr("画面尺寸", "Dimensions"), it) }
                FactRow(tr("馆藏", "Collection"), a.facts.museum)
                a.facts.accession?.let { FactRow(tr("藏品号", "Accession"), it) }
            }
            Divider()
            val story = a.story
            if (story != null) {
                Text(tr("创作背景", "Background"), color = WG.Ink, style = WGType.label)
                Text(story.text, color = WG.Ink, style = WGType.body)
                Text(story.attribution, color = WG.Secondary, style = WGType.small)
            } else {
                Text(tr("作品信息", "About this work"), color = WG.Ink, style = WGType.label)
                Text(
                    tr("基本资料来自馆方开放记录。可在原馆藏页面了解更多作品背景。", "Basic information comes from the museum's open collection record. Visit the source to learn more."),
                    color = WG.Ink, style = WGType.body,
                )
            }
            Divider()
            Text(tr("来源与许可", "Source and licence"), color = WG.Ink, style = WGType.label)
            a.image?.let { Text(it.credit, color = WG.Secondary, style = WGType.small) }
            a.model?.let { Text(it.credit, color = WG.Secondary, style = WGType.small); Text(it.note, color = WG.Secondary, style = WGType.small) }
            Text(
                tr("馆藏页面：", "Source page: ") + a.sourceUrl,
                color = WG.Sage, style = WGType.small,
                modifier = Modifier.galleryClickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.sourceUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
            )
            Spacer(Modifier.height(8.dp))
            if (binding == InfoBinding.FRAME) {
                PrimaryButton(tr("近看这幅", "Explore this work"), Modifier.fillMaxWidth()) {
                    // opens the Gallery viewer on this work; the frame keeps its own content (PRD 5.7)
                    val src = vm.frame.playlistSource
                    val ids = when (src) {
                        is QueueSource.FromCollection -> Catalog.collection(src.collectionId)?.artworkIds ?: listOf(a.id)
                        QueueSource.Favorites -> vm.favorites.toList().ifEmpty { listOf(a.id) }
                        is QueueSource.Search -> Catalog.search(src.query).map { it.id }.ifEmpty { listOf(a.id) }
                        null -> Catalog.collections.firstOrNull { it.artworkIds.contains(a.id) }?.artworkIds ?: listOf(a.id)
                    }
                    val source = src ?: Catalog.collections.firstOrNull { it.artworkIds.contains(a.id) }?.let { QueueSource.FromCollection(it.id) } ?: QueueSource.FromCollection("essentials")
                    vm.closeInfo()
                    vm.startQueue(source, ids, ids.indexOf(a.id).coerceAtLeast(0))
                }
                GhostButton(tr("收起介绍，继续展示", "Close details"), Modifier.fillMaxWidth()) { vm.closeInfo() }
            } else {
                GhostButton(tr("收起介绍", "Close details"), Modifier.fillMaxWidth()) { vm.closeInfo() }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
