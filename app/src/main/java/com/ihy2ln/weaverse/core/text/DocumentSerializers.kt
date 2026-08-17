package com.ihy2ln.weaverse.core.text

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val documentSerializersModule = SerializersModule {
    polymorphic(Block::class) {
        subclass(Paragraph::class)
        subclass(Heading::class)
        subclass(Quote::class)
        subclass(ListItem::class)
        subclass(Divider::class)
        subclass(MediaBlock::class)
        subclass(SceneBeatBlock::class)
        subclass(CodeBlock::class)
        subclass(MediaStackBlock::class)
        subclass(MediaGridBlock::class)
    }
}
