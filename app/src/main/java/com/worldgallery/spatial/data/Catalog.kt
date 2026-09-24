package com.worldgallery.spatial.data

enum class ArtCategory { PAINTING, SCULPTURE }

data class Artist(val nameZh: String, val nameEn: String, val datesZh: String, val datesEn: String) {
    val name: String get() = tr(nameZh, nameEn)
    val dates: String get() = tr(datesZh, datesEn)
}

/** Museum facts. Creation date and accession year are kept separate on purpose (see Metadata notes). */
data class Facts(
    val dateZh: String,
    val dateEn: String,
    val mediumZh: String,
    val mediumEn: String,
    /** Size of the work itself (not the frame); null when the record has no reliable value. */
    val dimensions: String?,
    val museumZh: String,
    val museumEn: String,
    val accession: String?,
) {
    val date: String get() = tr(dateZh, dateEn)
    val medium: String get() = tr(mediumZh, mediumEn)
    val museum: String get() = tr(museumZh, museumEn)
}

/** Background text with its own licence; null when only a fact card may be shown. */
data class Story(val zh: String, val en: String, val attributionZh: String, val attributionEn: String) {
    val text: String get() = tr(zh, en)
    val attribution: String get() = tr(attributionZh, attributionEn)
}

data class ImageAsset(
    val path: String,
    val thumbPath: String,
    val width: Int,
    val height: Int,
    val creditZh: String,
    val creditEn: String,
    /** Longest edge of the legal source file we hold; used for the "detail limit" hint. */
    val sourceLongEdge: Int,
) {
    val credit: String get() = tr(creditZh, creditEn)
}

data class ModelAsset(
    val id: String,
    /** Direct download URL. Null when the file must be obtained manually (e.g. a Sketchfab login) and pushed to files/models/<id>.<format>. */
    val downloadUrl: String?,
    val expectedBytes: Long?,
    val format: String,
    val licenseLabel: String,
    val sourcePage: String,
    val creditZh: String,
    val creditEn: String,
    val noteZh: String,
    val noteEn: String,
    /** Yaw (degrees) that turns the curator-selected front towards the viewer. */
    val frontYaw: Float = 0f,
    /** Height the model is displayed at in the Shared Space viewer (metres); Stage uses ~2.4x. */
    val displayHeight: Float = 0.42f,
) {
    val credit: String get() = tr(creditZh, creditEn)
    val note: String get() = tr(noteZh, noteEn)
}

data class Artwork(
    val id: String,
    val category: ArtCategory,
    val titleZh: String,
    val titleEn: String,
    val artist: Artist,
    val facts: Facts,
    val story: Story?,
    val image: ImageAsset?,
    /** Primary 3D presentation (sculptures). */
    val model: ModelAsset?,
    val sourceUrl: String,
    /** Preview picture for works whose model is not loaded yet. */
    val previewPath: String? = null,
) {
    val title: String get() = tr(titleZh, titleEn)
    val aspect: Float get() = image?.let { it.width.toFloat() / it.height } ?: (16f / 9f)
    val is3D: Boolean get() = category == ArtCategory.SCULPTURE
    val cardMeta: String get() = artist.name.substringAfterLast('·').trim().let { short -> tr(shortZh(), short) + " · " + facts.date }
    private fun shortZh(): String = artist.nameZh.substringAfterLast('·').trim()
}

data class Collection(
    val id: String,
    val category: ArtCategory,
    val titleZh: String,
    val titleEn: String,
    val subtitleZh: String,
    val subtitleEn: String,
    val introZh: String,
    val introEn: String,
    val artworkIds: List<String>,
    val coverId: String,
) {
    val title: String get() = tr(titleZh, titleEn)
    val subtitle: String get() = tr(subtitleZh, subtitleEn)
    val intro: String get() = tr(introZh, introEn)
}

/**
 * Published content only (PRD 3.2 "只显示已发布作品"). The four paintings ship with museum
 * open-access images that were fetched and resized offline (≤ 4096 px long edge, never upscaled); the sculptures are
 * SMK / Thomas Flynn cast scans that are loaded on demand (WG-03).
 */
object Catalog {
    private val vanGogh = Artist("文森特·梵高", "Vincent van Gogh", "1853–1890 · 荷兰", "1853–1890 · Dutch")
    private val hokusai = Artist("葛饰北斋", "Katsushika Hokusai", "1760–1849 · 日本", "1760–1849 · Japanese")
    private val monet = Artist("克洛德·莫奈", "Claude Monet", "1840–1926 · 法国", "1840–1926 · French")

    val artworks: List<Artwork> = listOf(
        Artwork(
            id = "bedroom", category = ArtCategory.PAINTING,
            titleZh = "阿尔勒的卧室", titleEn = "The Bedroom", artist = vanGogh,
            facts = Facts("1889", "1889", "布面油画", "Oil on canvas", "73.6 × 92.3 cm", "芝加哥艺术博物馆", "Art Institute of Chicago", "1926.417"),
            story = Story(
                zh = "画面描绘梵高在阿尔勒的卧室。这件芝加哥藏品是他于 1889 年绘制的第二个版本；他希望借色彩与空间的处理，表达休息和安宁。",
                en = "Van Gogh painted his bedroom in Arles in three versions. This second version dates to 1889. Through color and space, he sought to evoke rest and calm.",
                attributionZh = "馆方介绍：Art Institute of Chicago · CC BY 4.0 · 中文节译",
                attributionEn = "Text: Art Institute of Chicago · CC BY 4.0 · Edited excerpt",
            ),
            image = ImageAsset("artworks/bedroom.jpg", "thumbs/bedroom.jpg", 4096, 3266, "图像：Art Institute of Chicago · Public Domain（CC0）", "Image: Art Institute of Chicago · Public Domain (CC0)", 5573),
            model = null,
            sourceUrl = "https://www.artic.edu/artworks/28560",
        ),
        Artwork(
            id = "wheat", category = ArtCategory.PAINTING,
            titleZh = "柏树麦田", titleEn = "Wheat Field with Cypresses", artist = vanGogh,
            facts = Facts("1889", "1889", "布面油画", "Oil on canvas", "73.2 × 93.4 cm", "大都会艺术博物馆", "The Metropolitan Museum of Art", "1993.132"),
            story = Story(
                zh = "梵高在法国圣雷米描绘了麦田、柏树与起伏的天空。基本资料来自大都会开放馆藏记录；1993 是入藏年份，不是创作时间。",
                en = "Van Gogh depicted wheat fields, cypresses and a sweeping sky in Saint-Rémy, France. Facts come from The Met's open collection record; 1993 is the accession year, not the date of the painting.",
                attributionZh = "图像与基本资料：The Met · CC0；背景文字：编辑撰写",
                attributionEn = "Image and facts: The Met · CC0 · Background: editorial summary",
            ),
            image = ImageAsset("artworks/wheat.jpg", "thumbs/wheat.jpg", 4000, 3184, "图像：The Metropolitan Museum of Art · CC0", "Image: The Metropolitan Museum of Art · CC0", 4000),
            model = null,
            sourceUrl = "https://www.metmuseum.org/art/collection/search/436535",
        ),
        Artwork(
            id = "wave", category = ArtCategory.PAINTING,
            titleZh = "神奈川冲浪里", titleEn = "The Great Wave off Kanagawa", artist = hokusai,
            facts = Facts("约 1830–1833", "c. 1830–1833", "彩色木版画", "Color woodblock print", "25.7 × 37.9 cm", "芝加哥艺术博物馆", "Art Institute of Chicago", "1925.3245"),
            story = null,
            image = ImageAsset("artworks/wave.jpg", "thumbs/wave.jpg", 4096, 2803, "图像：Art Institute of Chicago · Public Domain（CC0）", "Image: Art Institute of Chicago · Public Domain (CC0)", 8242),
            model = null,
            sourceUrl = "https://www.artic.edu/artworks/24645",
        ),
        Artwork(
            id = "lilies", category = ArtCategory.PAINTING,
            titleZh = "睡莲", titleEn = "Water Lilies", artist = monet,
            facts = Facts("1906", "1906", "布面油画", "Oil on canvas", "89.9 × 94.1 cm", "芝加哥艺术博物馆", "Art Institute of Chicago", "1933.1157"),
            story = null,
            image = ImageAsset("artworks/lilies.jpg", "thumbs/lilies.jpg", 3000, 2887, "图像：Art Institute of Chicago · Public Domain（CC0）", "Image: Art Institute of Chicago · Public Domain (CC0)", 3000),
            model = null,
            sourceUrl = "https://www.artic.edu/artworks/16568",
        ),
        Artwork(
            id = "venus", category = ArtCategory.SCULPTURE,
            titleZh = "米洛的维纳斯", titleEn = "Venus de Milo",
            artist = Artist("作者不详（古希腊）", "Unknown (Ancient Greek)", "原作约公元前 150–125 年", "Original c. 150–125 BCE"),
            facts = Facts("约公元前 150–125 年", "c. 150–125 BCE", "石膏复制件扫描（原作为大理石）", "Scan of a plaster cast (original in marble)", null, "SMK 丹麦国家美术馆（复制件）", "SMK – National Gallery of Denmark (cast)", null),
            story = Story(
                zh = "原作信息与复制件扫描来源分开标注。模型可在头显中旋转、缩放，近看不同角度。",
                en = "The original work and the scanned cast are identified separately. Rotate and scale the model in the headset to explore it.",
                attributionZh = "文字：World Gallery 编辑", attributionEn = "Text: World Gallery editorial",
            ),
            image = null,
            model = ModelAsset(
                id = "venus", downloadUrl = null, expectedBytes = null, format = "glb",
                licenseLabel = "CC0", sourcePage = "https://sketchfab.com/3d-models/venus-de-milo-statuestexturingchallenge-smk-2983d92ac4e744f485492580ca7629f2",
                creditZh = "模型：SMK – National Gallery of Denmark · CC0 · 优化版约 2.4 万三角面", creditEn = "Model: SMK – National Gallery of Denmark · CC0 · optimised, ~24k triangles",
                noteZh = "Sketchfab 下载需要登录授权；取得文件后放到 files/models/venus.glb 即可加载。", noteEn = "Sketchfab downloads need a signed-in account; place the file at files/models/venus.glb to load it.",
                displayHeight = 0.46f,
            ),
            sourceUrl = "https://sketchfab.com/3d-models/venus-de-milo-statuestexturingchallenge-smk-2983d92ac4e744f485492580ca7629f2",
            previewPath = "artworks/venus.jpg",
        ),
        Artwork(
            id = "discobolus", category = ArtCategory.SCULPTURE,
            titleZh = "掷铁饼者", titleEn = "Discobolus",
            artist = Artist("米隆（罗马复制品）", "After Myron (Roman copy)", "原作约公元前 460–450 年", "Original c. 460–450 BCE"),
            facts = Facts("约公元前 460–450 年（原作）", "c. 460–450 BCE (original)", "石膏复制件扫描", "Scan of a plaster cast", null, "SMK 丹麦国家美术馆（复制件）", "SMK – National Gallery of Denmark (cast)", null),
            story = null, image = null,
            model = ModelAsset(
                id = "discobolus", downloadUrl = null, expectedBytes = null, format = "glb",
                licenseLabel = "CC0", sourcePage = "https://sketchfab.com/3d-models/discobolus-the-discus-thrower-50f50abf45414ad8a658325df7a5fcb3",
                creditZh = "模型：SMK – National Gallery of Denmark · CC0 · 约 20 万三角面", creditEn = "Model: SMK – National Gallery of Denmark · CC0 · ~200k triangles",
                noteZh = "Sketchfab 下载需要登录授权；取得文件后放到 files/models/discobolus.glb。", noteEn = "Sketchfab downloads need a signed-in account; place the file at files/models/discobolus.glb.",
                displayHeight = 0.46f,
            ),
            sourceUrl = "https://sketchfab.com/3d-models/discobolus-the-discus-thrower-50f50abf45414ad8a658325df7a5fcb3",
        ),
        Artwork(
            id = "david-head", category = ArtCategory.SCULPTURE,
            titleZh = "大卫头像", titleEn = "Head of Michelangelo's David",
            artist = Artist("米开朗基罗（复制件扫描）", "Michelangelo (cast scan)", "原作 1501–1504", "Original 1501–1504"),
            facts = Facts("1501–1504（原作）", "1501–1504 (original)", "复制件扫描优化版", "Optimised scan of a cast", null, "原始数据：SMK；优化：Thomas Flynn", "Source data: SMK; optimised by Thomas Flynn", null),
            story = null, image = null,
            model = ModelAsset(
                id = "david-head", downloadUrl = null, expectedBytes = null, format = "glb",
                licenseLabel = "CC BY", sourcePage = "https://sketchfab.com/3d-models/head-of-michelangelos-david-optimised-d29af50360624e5e9b1855666475380d",
                creditZh = "模型：Thomas Flynn（优化版，CC BY）· 原始扫描 SMK（CC0）· 约 10 万三角面", creditEn = "Model: Thomas Flynn (optimised, CC BY) · source scan SMK (CC0) · ~100k triangles",
                noteZh = "此优化版按 CC BY 署名，不标为 CC0。取得文件后放到 files/models/david-head.glb。", noteEn = "This optimised version is CC BY, not CC0. Place the file at files/models/david-head.glb.",
                displayHeight = 0.40f,
            ),
            sourceUrl = "https://sketchfab.com/3d-models/head-of-michelangelos-david-optimised-d29af50360624e5e9b1855666475380d",
        ),
    )

    val byId: Map<String, Artwork> = artworks.associateBy { it.id }

    val collections: List<Collection> = listOf(
        Collection("essentials", ArtCategory.PAINTING, "世界名画 · 精选", "World masterpieces", "4 件精选 · 逐件观赏", "4 works · View one at a time",
            "梵高、北斋与莫奈的代表作品。坐在原地，也能看清画面的细节。", "Discover Van Gogh, Hokusai and Monet. Explore every detail from the comfort of your seat.",
            listOf("bedroom", "wheat", "wave", "lilies"), "wave"),
        Collection("van-gogh", ArtCategory.PAINTING, "梵高 · 色彩与笔触", "Van Gogh · Color & gesture", "近看色彩与笔触", "Explore color and brushwork",
            "两幅 1889 年的作品：室内的安宁与田野的律动。", "Two works from 1889: the calm of a room and the rhythm of a field.",
            listOf("bedroom", "wheat"), "wheat"),
        Collection("monet", ArtCategory.PAINTING, "莫奈 · 光与花园", "Monet · Light & gardens", "从睡莲开始", "Begin with Water Lilies",
            "吉维尼的水面与光线。", "Water and light at Giverny.",
            listOf("lilies"), "lilies"),
        Collection("classic-sculpture", ArtCategory.SCULPTURE, "经典雕塑", "Classical sculpture", "3 件复制件扫描 · 按需加载", "3 cast scans · Loaded on demand",
            "让作品转过来：拖动转动模型、缩放看局部，无需绕行。", "Let the work turn: rotate the model and zoom into details without walking around.",
            listOf("venus", "discobolus", "david-head"), "venus"),
    )

    fun collection(id: String): Collection? = collections.firstOrNull { it.id == id }

    fun featured(): List<Collection> = collections.filter { it.category == ArtCategory.PAINTING }

    /** Chinese / English title, artist and museum search. */
    fun search(query: String): List<Artwork> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return artworks.filter { a ->
            listOf(a.titleZh, a.titleEn, a.artist.nameZh, a.artist.nameEn, a.facts.museumZh, a.facts.museumEn)
                .any { it.lowercase().contains(q) }
        }
    }
}
