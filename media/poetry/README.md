# 诗词熏听素材

此目录曾按用户于 2026-10-25 的要求，随代码保存到个人 Azure 私有仓库；这是当时的保存记录，不代表已获得公开再分发授权。内容对应 build61 / 3.8-sony.4 的最终 Edge 音频版本。

## 可直接复制到 U 盘

将 `usb/poetry` 文件夹复制到 U 盘根目录，成为 `poetry/poems.json` 和 `poetry/audio/…`。其中包含 87 份原始真人录音、87 份裁去教材介绍的朗诵、87 份 Edge 释义，以及目录、来源、剪辑范围和使用说明。原始目录另存为 `poems-original.json`，实际播放使用 `poems.json`。

真人诗名全部保留；53 首原有真人作者报幕保留，34 首只补作者名。释义及作者补读使用 `zh-CN-XiaoxiaoNeural`（晓晓），语速 -8%、音高不变。音频提前在电脑生成，电视播放时不联网。原诗结束后等待 3 秒读释义，释义结束后等待 5 秒进入下一首。

`samples/xiaochi.mp3` 是《小池》的完整试听：真人诗名、作者、正文，停 3 秒后接 Edge 释义。

## 使用保存的输入重新处理

`preparation/meaning` 保存 87 段原始 Edge 释义 MP3，`preparation/author` 保存 34 段作者补读 MP3；各自附有音色参数、文字、语音边界与哈希记录。使用已有音频重新处理时无需调用在线 TTS：

```text
python tools/prepare-poetry-listening.py --library media/poetry/usb/poetry --catalog media/poetry/usb/poetry/poems-original.json --meanings media/poetry/preparation/meaning --authors media/poetry/preparation/author --segments media/poetry/usb/poetry/listening-segments.json --destination work/poetry-rebuilt
```

需要 Python 3.10+、ffmpeg 和 ffprobe。输出目录必须为新目录。更新文字或音色时，使用 [Edge 生成工具](../../tools/synthesize-poetry-edge.py) 生成新输入，再处理；完整流程见 [诗词库维护](../../docs/POETRY_LIBRARY.zh-CN.md)。

`SHA256SUMS.json` 记录本目录各文件的 SHA-256 和字节数（校验清单本身除外）。Git 对此目录保留原始字节及换行，避免改变原目录与 Edge 语音记录中的指纹。`validation.json` 只保留可分享的构建、解码、响度、哈希和设备检查结论。原始设备记录、截图、网络失败记录、私钥和 APK 留在忽略提交的本地工作目录。

## 来源与使用范围

真人录音来自[教育部、中央广播电视总台中小学语文示范诵读库](https://edu.cnr.cn/eduzt/ywkwsfsd/)，逐首来源、播音员及原始文件哈希见 `usb/poetry/sources.json`；派生音频范围及哈希见 `usb/poetry/listening-sources.json`。释义文字为本项目简明改写。

录音、配乐及第三方内容的权利归原权利人所有，不属于代码 MIT 许可证的授权内容。素材原用于个人家庭学习和私有仓库保存；仓库保存或展示来源不代表已获准向公共仓库或平台重新发布这些录音。
