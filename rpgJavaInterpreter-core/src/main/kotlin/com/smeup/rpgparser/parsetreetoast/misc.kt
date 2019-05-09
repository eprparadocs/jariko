package com.smeup.rpgparser.parsetreetoast

import com.smeup.rpgparser.RpgParser.*
import com.smeup.rpgparser.ast.*
import com.smeup.rpgparser.ast.AssignmentOperator.DIVIDE_ASSIGNMENT
import com.smeup.rpgparser.ast.AssignmentOperator.NORMAL_ASSIGNMENT
import com.smeup.rpgparser.interpreter.*
import com.strumenta.kolasu.mapping.toPosition
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.Position
import com.strumenta.kolasu.model.ReferenceByName
import org.antlr.v4.runtime.ParserRuleContext

data class ToAstConfiguration(val considerPosition: Boolean = true, 
                              val compileTimeInterpreter : CompileTimeInterpreter = CommonCompileTimeInterpreter)

fun List<Node>.position() : Position? {
    val start = this.asSequence().map { it.position?.start }.filterNotNull().sorted().toList()
    val end = this.asSequence().map { it.position?.end }.filterNotNull().sorted().toList()
    return if (start.isEmpty() || end.isEmpty()) {
        null
    } else {
        Position(start.first(), end.last())
    }
}

fun RContext.toAst(conf : ToAstConfiguration = ToAstConfiguration()) : CompilationUnit {
    val dataDefinitions = this.statement()
            .mapNotNull {
                when {
                    it.dspec() != null -> it.dspec().toAst(conf)
                    it.dcl_ds() != null -> it.dcl_ds().toAst(conf)
                    else -> null
                }
            }
    val mainStmts = this.statement().mapNotNull {
        when {
            it.cspec_fixed() != null -> it.cspec_fixed().toAst(conf)
            it.block() != null -> it.block().toAst(conf)
            else -> null
        }
    }
    val subroutines = this.subroutine().map { it.toAst(conf) }
    return CompilationUnit(
            dataDefinitions,
            MainBody(mainStmts, if (conf.considerPosition) mainStmts.position() else null),
            subroutines,
            position = this.toPosition(conf.considerPosition))
}

internal fun SubroutineContext.toAst(conf : ToAstConfiguration = ToAstConfiguration()): Subroutine {
    return Subroutine(this.begsr().csBEGSR().factor1.content.text,
            this.statement().map { it.toAst(conf) },
            toPosition(conf.considerPosition))
}

internal fun FunctionContext.toAst(conf : ToAstConfiguration = ToAstConfiguration()): FunctionCall {
    return FunctionCall(ReferenceByName(this.functionName().text), this.args().expression().map { it.toAst(conf) }, toPosition(conf.considerPosition))
}

internal fun String.isInt() = this.toIntOrNull() != null

internal fun ParserRuleContext.rContext(): RContext {
    return if (this.parent == null) {
        this as RContext
    } else {
        (this.parent as ParserRuleContext).rContext()
    }
}

internal fun FactorContentContext.toAst(conf: ToAstConfiguration): Expression {
    return IntLiteral(this.CS_FactorContent().text.toLong(), position = toPosition(conf.considerPosition))
}

internal fun SymbolicConstantsContext.toAst(conf : ToAstConfiguration = ToAstConfiguration()) : Expression {
    return when {
        this.SPLAT_HIVAL() != null -> HiValExpr(toPosition(conf.considerPosition))
        this.SPLAT_LOVAL() != null -> LowValExpr(toPosition(conf.considerPosition))
        else -> TODO()
    }
}

internal fun Cspec_fixedContext.toAst(conf : ToAstConfiguration = ToAstConfiguration()): Statement {
    return when {
        this.cspec_fixed_standard() != null -> this.cspec_fixed_standard().toAst(conf)
        else -> TODO(this.text.toString())
    }
}

internal fun Cspec_fixed_standardContext.toAst(conf : ToAstConfiguration = ToAstConfiguration()): Statement {
    return when {
        this.csEXSR() != null -> this.csEXSR().toAst(conf)
        this.csEVAL() != null -> this.csEVAL().toAst(conf)
        this.csCALL() != null -> this.csCALL().toAst(conf)
        this.csSETON() != null -> this.csSETON().toAst(conf)
        this.csPLIST() != null -> this.csPLIST().toAst(conf)
        this.csCLEAR() != null -> this.csCLEAR().toAst(conf)
        this.csLEAVE() != null -> LeaveStmt(toPosition(conf.considerPosition))
        this.csITER() != null -> IterStmt(toPosition(conf.considerPosition))
        this.csOTHER() != null -> OtherStmt(toPosition(conf.considerPosition))
        this.csDSPLY() != null -> this.csDSPLY().toAst(conf)
        else -> TODO("${this.text} at ${this.toPosition(true)}")
    }
}

// FIXME: This is very, very, very ugly. It should be fixed by parsing this properly
//        in the grammar
internal fun referenceToExpression(text: String, position: Position?) : Expression {
    var expr : Expression = text.indexOf("(").let {
        val varName = if (it == -1) text else text.substring(0, it)
        DataRefExpr(ReferenceByName(varName))
    }
    if (text.contains("(")) {
        // TODO support annidated parenthesis, if necessary
        if (text.substring(text.indexOf("(") + 1).contains("(")) {
            TODO("Support annidated parenthesis")
        }
        val indexText = text.substring(text.indexOf("(") + 1, text.lastIndexOf(")"))
        expr = ArrayAccessExpr(expr, IntLiteral(indexText.toLong(),
                if (position == null) null else Position(position.start.plus(text.substring(0, text.indexOf("("))),
                        position.start.plus(text.substring(0, text.lastIndexOf(")"))))))
    }
    return expr
}

internal fun CsDSPLYContext.toAst(conf : ToAstConfiguration = ToAstConfiguration()): DisplayStmt {
    val expression = this.cspec_fixed_standard_parts().result.toAst(conf)
    return DisplayStmt(expression, toPosition(conf.considerPosition))
}

internal fun ResultTypeContext.toAst(conf : ToAstConfiguration = ToAstConfiguration()): Expression {
    // TODO this should have been parsed differently because here we have to figure out
    // what kind of expression is this
    return DataRefExpr(ReferenceByName(this.text), toPosition(conf.considerPosition))
}

internal fun CsCLEARContext.toAst(conf : ToAstConfiguration = ToAstConfiguration()): ClearStmt {
    val name = this.cspec_fixed_standard_parts().result.text
    var dataDeclaration : InStatementDataDefinition? = null
    if (!this.cspec_fixed_standard_parts().len.text.isBlank()) {
        val length = this.cspec_fixed_standard_parts().len.text.trim().toInt()
        dataDeclaration = InStatementDataDefinition(name, StringType(length.toLong()), toPosition(conf.considerPosition))
    }
    return ClearStmt(
            referenceToExpression(name, toPosition(conf.considerPosition)),
            dataDeclaration,
            toPosition(conf.considerPosition))
}

internal fun CsPLISTContext.toAst(conf : ToAstConfiguration = ToAstConfiguration()): PlistStmt {
    val isEntry = ((this.parent as Cspec_fixed_standardContext).parent as Cspec_fixedContext).factor().symbolicConstants().SPLAT_ENTRY() != null
    return PlistStmt(
            this.csPARM().map { it.toAst(conf) },
            isEntry,
            toPosition(conf.considerPosition)
    )
}

internal fun CsPARMContext.toAst(conf : ToAstConfiguration = ToAstConfiguration()): PlistParam {
    val paramName = this.cspec_fixed_standard_parts().result.CS_FactorContent().text
    return PlistParam(ReferenceByName(paramName), toPosition(conf.considerPosition))
}

internal fun CsSETONContext.toAst(conf : ToAstConfiguration = ToAstConfiguration()): SetOnStmt {
    return SetOnStmt(indicators(this.cspec_fixed_standard_parts()), toPosition(conf.considerPosition))
}

internal fun indicators(cspecs: Cspec_fixed_standard_partsContext) : List<DataWrapUpChoice> {
    return listOf(cspecs.hi, cspecs.lo, cspecs.eq)
            .asSequence()
            .map { it.text }
            .filter { !it.isNullOrBlank() }
            .map (String::toUpperCase)
            .map(DataWrapUpChoice::valueOf)
            .toList()
}

internal fun CsEXSRContext.toAst(conf : ToAstConfiguration = ToAstConfiguration()): ExecuteSubroutine {
    val subroutineName = this.cspec_fixed_standard_parts().factor2.text
    require(this.cspec_fixed_standard_parts().decimalPositions.text.isBlank())
    require(this.cspec_fixed_standard_parts().eq.text.isBlank())
    require(this.cspec_fixed_standard_parts().hi.text.isBlank())
    require(this.cspec_fixed_standard_parts().len.text.isBlank())
    require(this.cspec_fixed_standard_parts().lo.text.isBlank())
    require(this.cspec_fixed_standard_parts().result.text.isBlank())
    return ExecuteSubroutine(ReferenceByName(subroutineName), toPosition(conf.considerPosition))
}

internal fun CsEVALContext.toAst(conf : ToAstConfiguration = ToAstConfiguration()): EvalStmt {
    return EvalStmt(
            this.target().toAst(conf),
            this.fixedexpression.expression().toAst(conf),
            operator=this.operator.toAssignmentOperator(),
            position=toPosition(conf.considerPosition))
}

internal fun TargetContext.toAst(conf : ToAstConfiguration = ToAstConfiguration()): AssignableExpression {
    return when (this) {
        is SimpleTargetContext -> DataRefExpr(ReferenceByName(this.name.text), toPosition(conf.considerPosition))
        is IndexedTargetContext -> ArrayAccessExpr(array=this.base.toAst(conf),
                index = this.index.toAst(conf),
                position = toPosition(conf.considerPosition))
        else -> TODO()
    }
}

internal fun AssignmentOperatorIncludingEqualContext.toAssignmentOperator(): AssignmentOperator {
    return when {
        this.CDIV() != null -> DIVIDE_ASSIGNMENT
        this.EQUAL() != null -> NORMAL_ASSIGNMENT
        else -> throw UnsupportedOperationException(this.text)
    }
}

internal fun CsCALLContext.toAst(conf : ToAstConfiguration = ToAstConfiguration()): CallStmt {
    require(this.cspec_fixed_standard_parts().factor().factorContent().size == 1)
    val literal = this.cspec_fixed_standard_parts().factor().factorContent()[0].literal()
    return CallStmt(literal.toAst(conf),
            this.csPARM().map { it.toAst(conf) },
            toPosition(conf.considerPosition))
}