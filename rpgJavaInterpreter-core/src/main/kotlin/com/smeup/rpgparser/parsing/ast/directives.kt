package com.smeup.rpgparser.parsing.ast

import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.Position

abstract class Directive(override val position: Position? = null) : Node(position)

data class DeceditDirective(val format: String, override val position: Position? = null) : Directive(position)