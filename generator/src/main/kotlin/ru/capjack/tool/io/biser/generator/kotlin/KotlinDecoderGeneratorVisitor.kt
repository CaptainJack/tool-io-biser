package ru.capjack.tool.io.biser.generator.kotlin

import ru.capjack.tool.io.biser.generator.CodeBlock
import ru.capjack.tool.io.biser.generator.model.EntityDescriptor
import ru.capjack.tool.io.biser.generator.model.EnumDescriptor
import ru.capjack.tool.io.biser.generator.model.ListType
import ru.capjack.tool.io.biser.generator.model.NullableType
import ru.capjack.tool.io.biser.generator.model.PrimitiveType
import ru.capjack.tool.io.biser.generator.model.StructureDescriptorVisitor
import ru.capjack.tool.io.biser.generator.model.StructureType
import ru.capjack.tool.io.biser.generator.model.Type
import ru.capjack.tool.io.biser.generator.model.TypeVisitor

class KotlinDecoderGeneratorVisitor(
	private val readCalls: TypeVisitor<String, Unit>,
	private val decoderNames: TypeVisitor<String, Unit>,
	private val typeNames: TypeVisitor<String, Unit>,
	structuresPackage: String
) : TypeVisitor<Unit, KotlinGeneratorContext>,
	StructureDescriptorVisitor<Unit, KotlinGeneratorContext> {
	
	private val structuresPackagePrefix = "$structuresPackage."
	
	private val entityFieldTypesVisitor = object :
		TypeVisitor<Unit, KotlinGeneratorContext> {
		override fun visitPrimitiveType(type: PrimitiveType, data: KotlinGeneratorContext) {
			data.imports.addImport("ru.capjack.tool.io.biser.Decoders")
		}
		
		override fun visitListType(type: ListType, data: KotlinGeneratorContext) {
			data.types.add(type.element)
		}
		
		override fun visitStructureType(type: StructureType, data: KotlinGeneratorContext) {
			data.types.add(type)
		}
		
		override fun visitNullableType(type: NullableType, data: KotlinGeneratorContext) {
			data.types.add(type)
		}
		
	}
	
	override fun visitPrimitiveType(type: PrimitiveType, data: KotlinGeneratorContext) {
	}
	
	override fun visitListType(type: ListType, data: KotlinGeneratorContext) {
		writeDeclaration(type, data).apply {
			line(readCalls.visitListType(type, Unit))
		}
	}
	
	override fun visitStructureType(type: StructureType, data: KotlinGeneratorContext) {
		data.imports.addImport(structuresPackagePrefix + type.name)
		type.descriptor.accept(this, data)
	}
	
	override fun visitNullableType(type: NullableType, data: KotlinGeneratorContext) {
		writeDeclaration(type, data).apply {
			line("if (readInt() == 0) null else ${type.original.accept(decoderNames)}()")
		}
	}
	
	override fun visitEnumStructureDescriptor(descriptor: EnumDescriptor, data: KotlinGeneratorContext) {
		val type = descriptor.type
		val typeName = type.accept(typeNames)
		writeDeclaration(type, data).apply {
			identBracketsCurly("when (val id = readInt()) ") {
				descriptor.values.forEach {
					line("${it.id} -> $typeName.${it.name}")
				}
				data.imports.addImport("ru.capjack.tool.io.biser.UnknownIdDecoderException")
				line("else -> throw UnknownIdDecoderException(id, $typeName::class)")
			}
		}
	}
	
	
	override fun visitEntityStructureDescriptor(descriptor: EntityDescriptor, data: KotlinGeneratorContext) {
		val type = descriptor.type
		
		if (descriptor.children.isNotEmpty()) {
			
			if (!descriptor.abstract) {
				writeDeclaration(type, data, true).apply {
					writeEntityDecode(descriptor, data)
				}
			}
			
			writeDeclaration(type, data).apply {
				identBracketsCurly("when (val id = readInt()) ") {
					descriptor.allChildren.forEach { child ->
						val childDescriptor = child.descriptor as EntityDescriptor
						if (!childDescriptor.abstract) {
							val name = child.accept(decoderNames)
							if (childDescriptor.children.isNotEmpty()) {
								line("${childDescriptor.id} -> ${name}_RAW()")
							}
							else {
								line("${childDescriptor.id} -> $name()")
							}
						}
					}
					
					if (!descriptor.abstract) {
						val name = descriptor.type.accept(decoderNames)
						line("${descriptor.id} -> ${name}_RAW()")
					}
					
					val typeName = type.accept(typeNames)
					data.imports.addImport("ru.capjack.tool.io.biser.UnknownIdDecoderException")
					line("else -> throw UnknownIdDecoderException(id, $typeName::class)")
				}
			}
		}
		else {
			writeDeclaration(type, data).apply {
				writeEntityDecode(descriptor, data)
			}
		}
	}
	
	private fun CodeBlock.writeEntityDecode(descriptor: EntityDescriptor, data: KotlinGeneratorContext) {
		identBracketsRound(descriptor.type.name.substringAfterLast('.')) {
			val last = descriptor.fields.size - 1
			descriptor.fields.forEachIndexed { i, field ->
				field.type.accept(entityFieldTypesVisitor, data)
				line(field.type.accept(readCalls) + (if (i == last) "" else ","))
			}
		}
	}
	
	private fun writeDeclaration(type: Type, context: KotlinGeneratorContext, raw: Boolean = false): CodeBlock {
		context.imports.addImport("ru.capjack.tool.io.biser.Decoder")
		
		var name = type.accept(decoderNames)
		val typeName = type.accept(typeNames)
		
		if (raw) {
			name += "_RAW"
		}
		
		val block = context.code.identBracketsCurly((if (raw) "private " else "") + "val $name: Decoder<$typeName> = ")
		
		context.code.line()
		
		return block
	}
}