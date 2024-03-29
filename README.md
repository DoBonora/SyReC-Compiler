# General Information

This project was developed to attain the Bachelors Degree at the *Johannes Kepler University*. It implements the SyReC grammar and synthesises it into the Revlib REAL format.

# Implemented Functionality
The following table shows for which functionality a building block is already present in the code:
|                    | implemented | partly | not implemented |
|--------------------|:-------------:|:--------:|:-----------------:|
| ***Statements***         |        | X   |            |
| **call**               | X        |   |            |
| **for**                | X        |   |            |
| **if**                 | X        |   |            |
|                    |             |        |                 |
| **assign**             | X        |   |            |
| XOR assign         | X        |   |            |
| PLUS assign        | X        |   |            |
| MINUS assign       | X        |   |            |
|                    |             |        |                 |
| **unary**              | X        |   |            |
| negation           | X        |   |            |
| increment          | X        |   |            |
| decrement          | X        |   |            |
|                    |             |        |                 |
| **swap**               | X        |   |            |
| **skip**               | X        |   |            |
|                    |             |        |                 |
| **signal**             |        | X   |            |
| bus                | X        |   |            |
| array              |        |   | X            |
|                    |             |        |                 |
|                    |             |        |                 |
| ***Expressions***        |        | X   |            |
| **unary**              | X        |   |            |
| logical negation   | X        |   |            |
| binary negation    | X        |   |            |
|                    |             |        |                 |
| **shift**              | X        |   |            |
|                    |             |        |                 |
| **binary expressions** |        | X   |            |
| plus               | X        |   |            |
| minus              | X        |   |            |
| xor                | X        |   |            |
| times              |        |   | X            |
| divide             |        |   | X            |
| modulo             |        |   | X            |
| times upper        |        |   | X            |
| and                | X        |   |            |
| or                 | X        |   |            |
| bitwise and        | X        |   |            |
| bitwise or         |        |   | X            |
| less than          |        |   | X            |
| greater than       |        |   | X            |
| equal              |        |   | X            |
| not equal          |        |   | X            |
| less or equal      |        |   | X            |
| greater or equal   |        |   | X            |

# Project Structure
The project is structured in 3 main steps, the parsing, the generation of the gate list and the writing of the gate list in the REAL format.

## Parser
The parser and scanner is written using the tool Coco/R developed by the *SSW Institute* of the *Johannes Kepler University*. This allows us to write the parser using
attributed grammar. For more information check [their website](https://ssw.jku.at/Research/Projects/Coco/). 
In the Parser a file written in SyReC Grammar is read and all the elements of the grammar are created as AbstractSyntaxTree objects. The parser also creates a symbol table for
needed checks, such as if a module or signal was defined or not. It is also used to save information such as the bit-width of lines or if they are an input or an output.
Because the SyReC Grammar is not LL1 we need to have helper functions to determine the correct productions in some cases. The modules not only have a symbol table representation
but also a representation for generating the gates, this gets created when parsing the beginning of a module, and gets finished when ending it.

## Abstract Syntax Tree
The Abstract Syntax Tree (AST for short) contains three kinds of classes.
### Statements
The statement class is an abstract class from which all concrete statements inherit. It has constructor, a function to generate the gates needed and a function to replace
the signals used in call statements (as a call statement can overwrite the used lines of a previous module with new lines).
Depending on the type of statement they can contain Expressions, one or more Statements and sometimes identifiers for loop variables and similar.
Like most parts of the AST the functions first do the needed work for the current statement and then call the generate function of every contained part.

### Expressions
The second kind of classes are Expressions. Just like with statements all classes inherit from an abstract Expression class. Expressions, just like statements have a generate
and replace signal function but also have additional functions like resetting the lines used by the expression if they can be set back to zero (via lineAware synthesis for
example). They also have an additional function to get the Width of the expression and to get the signals contained in the expression (used for checks such as if the
programmer tries to write an expression containing a signal to the same signal). Most expressions contain sub expressions except for the basic classes for numbers and signals.

#### Number Expressions
Number Expressions are used to save numbers. They either are a local variable like a loop var or a complete calculation.

#### Signal Expressions
Signal Expressions are used to save physical lines in the module. They have a list of lines, a name, and a width. If the line is a bus line, the name is generated by adding
the _x where x is the line number to the name.

### Code Module
The CodeMod class is a dataobject to hold the code representation of a module. This means saving the variables defined in the source code, keeping track of additional lines (used for expressions), keeping track of additional lines that are zero and can be used and any loop variables used. Whenever additional lines are needed when generating
an expression they can be generated from the code module, which returns a Signal Expression. If the programmer decides that line aware synthesis is needed, the lines can be
reset back to zero after the expression is used, after which the code module needs to be notified to reset the lines back to zero to mark them as usable again.
## Code Generation
The Code generation happens when a module gets finished in the parser. Two helper classes are used to facilitate this.
### Gate
Gates are a class that are used to represent the different kinds of reversible gates there are. They save what kind of gate they are, what target lines they have and what
control lines they have.
### Expression Result
The Expression Result is the class used when evaluating the Expressions of the AST. Because an expression can either just be a signal, a number or an already generated list
of gates (with a result being written to lines already) this class needs to handle and unify all these cases. If the result for example is just a number, we can use it as
such and use it to simplify some code (for example with loop vars and if statements).
### Code
In this class all the code generation happens. The end module class has all the information to write the resulting gate list to the REAL format. in this function the 
generate function is called, which in turn calls the generate function of all the sub-parts. After writing all the meta information using the Code Module class, the
gate list gets written as specified by the REAL standard. This means writing the symbol of the gate, the involved signal count, all the control lines and finally
all the target lines.  
Apart from this functionality, the Code class also has functions for every kind of operation. These functions take a varying amount of ExpressionResults or SignalExpressions
and use these to generate the resulting list of gates. Expressions usually need new lines to save the result to, so these lines need to be provided to the functions using
the SignalExpression class. When ExpressionResults are involved a check may be needed to see if it is a number or just some lines. The generation of the gate list differs
if a signal is incremented by a constant compared to when it is incremented by a second signal. This can also be used, to call the minus function when adding a negative
number for example.
