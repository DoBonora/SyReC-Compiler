package CodeGen;


import AbstractSyntaxTree.CodeMod;
import AbstractSyntaxTree.SignalExpression;
import SymTable.Mod;
import SymTable.Obj;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;

import static CodeGen.Gate.Kind.*;

public class Code {


    public static CodeMod createModule(Mod module) {
        return new CodeMod(module.name, module.getLocals());
    }

    public static void endModule(String folderName, Mod module, CodeMod curMod) {
        //ends the module and writes it to file
        Path curPath = Path.of(folderName);
        try {
            Files.createDirectory(curPath);
        } catch (FileAlreadyExistsException ee) {
            //do nothing as the directory already exists
        } catch (IOException e) {
            System.err.println("Failed to create directory!" + e.getMessage());
        }
        try { //delete File if it already exists
            Files.deleteIfExists(Path.of(curPath.toString(), module.name + ".real"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            //create Writer
            BufferedWriter curWriter = Files.newBufferedWriter(Path.of(curPath.toString(), module.name + ".real"), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            ArrayList<Gate> gates = curMod.generate();
            curWriter.append("# ").append(module.name);
            curWriter.newLine();
            curWriter.append(".version 2.0");
            curWriter.newLine();
            curWriter.append(".numvars ").append(String.valueOf(curMod.getVarCount()));
            curWriter.newLine();
            ArrayList<Obj> lines = curMod.getVariables();
            curWriter.append(".variables ");
            for (Obj line : lines) {
                if (line.width == 1) {
                    curWriter.append(line.name).append(" "); //write each variable
                } else for (int i = 0; i < line.width; i++) {
                    curWriter.append(line.name).append("_").append(String.valueOf(i)).append(" "); //write all subvariables of the width
                }
            }
            //TODO inputs and outputs (optional and not specified in SyReC)
            curWriter.newLine();
            curWriter.append(".constants ");
            for (Obj line : lines) {
                if (line.getConstant()) {
                    curWriter.append(String.join("", Collections.nCopies(line.width, "0")));
                } else {
                    curWriter.append(String.join("", Collections.nCopies(line.width, "-")));
                }
            }
            curWriter.newLine();
            curWriter.append(".garbage ");
            for (Obj line : lines) {
                if (line.getGarbage()) {
                    curWriter.append(String.join("", Collections.nCopies(line.width, "1")));
                } else {
                    curWriter.append(String.join("", Collections.nCopies(line.width, "-")));
                }
            }
            curWriter.newLine();
            curWriter.append(".begin");
            curWriter.newLine();

            //here the gates start
            for (Gate gate : gates) {
                ArrayList<String> controlLines = gate.getControlLines();
                ArrayList<String> targetLines = gate.getTargetLines();
                switch (gate.kind) {

                    case Toffoli:
                        curWriter.append("t");
                        break;
                    case Fredkin:
                        curWriter.append("f");
                        break;
                    case Peres:
                        curWriter.append("p");
                        break;
                    case V:
                        curWriter.append("v");
                        break;
                    case Vplus:
                        curWriter.append("v+");
                        break;
                    case Placeholder:
                        curWriter.append("unimplemented");
                        System.out.println("Warning, Placeholder Gate was used");
                        break;
                }
                curWriter.append(String.valueOf(controlLines.size() + targetLines.size()));
                for (String line : controlLines) {
                    curWriter.append(" ").append(line);
                }
                for (String line : targetLines) {
                    curWriter.append(" ").append(line);
                }
                curWriter.newLine();
            }
            curWriter.append(".end");
            curWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Function to generate Placeholder gates (to see if functions for a given SyReC File are missing
    public static ArrayList<Gate> placeholder() {
        ArrayList<Gate> gates = new ArrayList<>();
        Gate placeholderGate = new Gate(Placeholder);
        gates.add(placeholderGate);
        return gates;
    }

    //swap of two signals
    //function is only called if the width is equal
    public static ArrayList<Gate> swap(SignalExpression firstSig, SignalExpression secondSig) {
        ArrayList<Gate> gates = new ArrayList<>();
        for (int i = 0; i < firstSig.getWidth(); i++) {
            Gate tempGate = new Gate(Fredkin);
            tempGate.addTargetLine(firstSig.getLineName(i));
            tempGate.addTargetLine(secondSig.getLineName(i));
            gates.add(tempGate);
        }
        return gates;
    }

    //negate given Signal
    public static ArrayList<Gate> not(SignalExpression sig) {
        ArrayList<Gate> gates = new ArrayList<>();
        for (int i = 0; i < sig.getWidth(); i++) {
            Gate tempGate = new Gate(Toffoli);
            tempGate.addTargetLine(sig.getLineName(i));
            gates.add(tempGate);
        }
        return gates;
    }


    //++= Statement
    public static ArrayList<Gate> increment(SignalExpression sig) {
        ArrayList<Gate> gates = new ArrayList<>();
        for (int i = sig.getWidth() - 1; i >= 0; i--) {
            ArrayList<String> controlLines = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                controlLines.add(sig.getLineName(j));
            }
            Gate tempGate = new Gate(Toffoli);
            tempGate.addTargetLine(sig.getLineName(i));
            tempGate.addControlLines(controlLines);
            gates.add(tempGate);
        }
        return gates;
    }

    //--= Statement, plusplus but in reverse
    public static ArrayList<Gate> decrement(SignalExpression sig) {
        ArrayList<Gate> gates = increment(sig);
        Collections.reverse(gates);
        return gates;
    }

    public static ArrayList<Gate> leftShift(ExpressionResult exp, int number, SignalExpression additionalLines) {
        ArrayList<Gate> gates = new ArrayList<>();
        for (int i = 0; i < exp.getWidth() - number; i++) {
            Gate tempGate = new Gate(Toffoli);
            tempGate.addTargetLine(additionalLines.getLineName(i + number));
            tempGate.addControlLine(exp.getLineName(i));
            gates.add(tempGate);
        }
        return gates;
    }

    public static ArrayList<Gate> rightShift(ExpressionResult exp, int number, SignalExpression additionalLines) {
        ArrayList<Gate> gates = new ArrayList<>();
        for (int i = 0; i < exp.getWidth() - number; i++) {
            Gate tempGate = new Gate(Toffoli);
            tempGate.addTargetLine(additionalLines.getLineName(i));
            tempGate.addControlLine(exp.getLineName(i + number));
            gates.add(tempGate);
        }
        return gates;
    }

    public static ArrayList<Gate> notExp(ExpressionResult exp, SignalExpression additionalLines) {
        ArrayList<Gate> gates = new ArrayList<>();
        for (int i = 0; i < exp.getWidth(); i++) {
            Gate tempGate = new Gate(Toffoli);
            tempGate.addTargetLine(additionalLines.getLineName(i));
            tempGate.addControlLine(exp.getLineName(i));
            gates.add(tempGate); // line.i = !oldLine.i
            tempGate = new Gate(Toffoli);
            tempGate.addTargetLine(additionalLines.getLineName(i));
            gates.add(tempGate); // line.i = !line.i
        }
        return gates;
    }

    public static ArrayList<Gate> xorAssign(SignalExpression firstSignal, ExpressionResult exp) {
        ArrayList<Gate> gates = new ArrayList<>();
        if (exp.isNumber) {
            int number = exp.number;
            for (int i = 0; number != 0; i++) {
                if (number % 2 == 1) {
                    Gate newGate = new Gate(Toffoli);
                    newGate.addTargetLine(firstSignal.getLineName(i));
                    gates.add(newGate);
                }
                number /= 2;
            }
        } else {
            for (int i = 0; i < firstSignal.getWidth(); i++) {
                Gate newGate = new Gate(Toffoli);
                newGate.addTargetLine(firstSignal.getLineName(i));
                newGate.addControlLine(exp.getLineName(i));
                gates.add(newGate);
            }
        }
        return gates;
    }

    public static ArrayList<Gate> logicalAnd(ExpressionResult firstExp, ExpressionResult secondExp, SignalExpression additionalLine) {
        ArrayList<String> controlLines = new ArrayList<>();
        controlLines.addAll(firstExp.getLines());
        controlLines.addAll(secondExp.getLines());
        ArrayList<Gate> gates = new ArrayList<>();
        Gate tempGate = new Gate(Toffoli);
        tempGate.addTargetLine(additionalLine.getLineName(0));
        tempGate.addControlLines(controlLines);
        gates.add(tempGate);
        return gates;
    }

    public static ArrayList<Gate> logicalOr(ExpressionResult firstExp, ExpressionResult secondExp, SignalExpression additionalLine) {
        ArrayList<String> controlLines = new ArrayList<>();
        controlLines.addAll(firstExp.getLines());
        controlLines.addAll(secondExp.getLines());
        ArrayList<Gate> gates = new ArrayList<>();

        Gate tempGate = new Gate(Toffoli);
        tempGate.addTargetLine(firstExp.getLineName(0));
        gates.add(tempGate); // !a

        tempGate = new Gate(Toffoli);
        tempGate.addTargetLine(secondExp.getLineName(0));
        gates.add(tempGate); // !b

        tempGate = new Gate(Toffoli);
        tempGate.addTargetLine(additionalLine.getLineName(0));
        tempGate.addControlLines(controlLines);
        gates.add(tempGate); // !a and !b

        tempGate = new Gate(Toffoli);
        tempGate.addTargetLine(additionalLine.getLineName(0));
        gates.add(tempGate); // nand

        tempGate = new Gate(Toffoli);
        tempGate.addTargetLine(firstExp.getLineName(0));
        gates.add(tempGate); // !a to make the line usable again

        tempGate = new Gate(Toffoli);
        tempGate.addTargetLine(secondExp.getLineName(0));
        gates.add(tempGate); // !b to make the line usable again

        return gates;
    }

    public static ArrayList<Gate> plusAssign(SignalExpression signalExp, ExpressionResult res) {
        ArrayList<Gate> gates = new ArrayList<>();
        if (res.isNumber) {
            //both cant be a number because else the result would be handled by the AST
            int number = res.number;
            if (number == 0) {
                //neutral operation, return empty list
                return gates;
            }
            if (number < 0) {
                //if we add a negative number we can just use minus
                //because its a number we dont need the twosComplementLine
                ExpressionResult negative = new ExpressionResult(-number);
                return minusAssign(signalExp, negative);
            }
            ArrayList<Boolean> numBool = intToBool(number);
            while (numBool.size() < signalExp.getWidth()) {
                numBool.add(false);
            }
            ArrayList<Boolean> numBoolCopy = new ArrayList<>(numBool);
            //we now have firstExp as arbitrary Expression and a BooleanList for the number
            //general case, both are lines
            for (int i = 1; i < numBool.size(); i++) {
                //xn ^= yn
                if (numBool.get(i)) {
                    Gate tempGate = new Gate(Toffoli);
                    tempGate.addTargetLine(signalExp.getLineName(i));
                    gates.add(tempGate);
                }
            }
            for (int i = numBool.size() - 1; i > 1; i--) {
                //yn ^= yn-1 till y2 ^= y1
                numBool.set(i, numBool.get(i) ^ numBool.get(i - 1));
            }

            for (int i = numBool.size() - 1; i > 0; i--) {
                //yn ^= xn-1 & yn-1 and xn ^= yn but we cant write to yn so we do it directly
                //ax -> a1
                for (int j = i; j >= 0; j--) {
                    //bx->b0
                    if (numBool.get(j)) {
                        Gate tempGate = new Gate(Toffoli);
                        tempGate.addTargetLine(signalExp.getLineName(i));
                        for (int k = i - 1; k >= j; k--) {
                            tempGate.addControlLine(signalExp.getLineName(k));
                        }
                        gates.add(tempGate);
                    }

                }
            }
            for (int i = 0; i < numBoolCopy.size(); i++) {
                //yn ^= x1n
                if (numBoolCopy.get(i)) {
                    Gate tempGate = new Gate(Toffoli);
                    tempGate.addTargetLine(signalExp.getLineName(i));
                    gates.add(tempGate);
                }
            }

        } else {
            //general case, both are lines
            for (int i = 1; i < res.getWidth(); i++) {
                //xn ^= yn
                Gate tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(signalExp.getLineName(i));
                tempGate.addControlLine(res.getLineName(i));
                gates.add(tempGate);

            }
            for (int i = res.getWidth() - 1; i > 1; i--) {
                //yn ^= yn-1 till y2 ^= y1
                Gate tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(res.getLineName(i));
                tempGate.addControlLine(res.getLineName(i - 1));
                gates.add(tempGate);
            }
            int gatenum;
            for (gatenum = 1; gatenum < signalExp.getWidth() && gatenum < res.getWidth(); gatenum++) {
                //yn ^= xn-1 & yn-1
                Gate tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(res.getLineName(gatenum));
                tempGate.addControlLine(signalExp.getLineName(gatenum - 1));
                tempGate.addControlLine(res.getLineName(gatenum - 1));
                gates.add(tempGate);
            }
            gatenum--;
            for (int i = gatenum; i > 0; i--) {
                //xn ^= yn
                Gate tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(signalExp.getLineName(i));
                tempGate.addControlLine(res.getLineName(i));
                gates.add(tempGate);
                //reversal of yn ^= xn-1 & yn-1
                tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(res.getLineName(gatenum));
                tempGate.addControlLine(signalExp.getLineName(gatenum - 1));
                tempGate.addControlLine(res.getLineName(gatenum - 1));
                gates.add(tempGate);
            }
            for (int i = 2; i < res.getWidth(); i++) {
                //reversal of yn ^= yn-1 till y2 ^= y1
                Gate tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(res.getLineName(i));
                tempGate.addControlLine(res.getLineName(i - 1));
                gates.add(tempGate);
            }
            for (int i = 0; i < res.getWidth(); i++) {
                //yn ^= x1n
                Gate tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(signalExp.getLineName(i));
                tempGate.addControlLine(res.getLineName(i));
                gates.add(tempGate);

            }
        }

        return gates;
    }

    public static ArrayList<Gate> minusAssign(SignalExpression signalExp, ExpressionResult res) {
        ArrayList<Gate> gates = new ArrayList<>();
        if (res.isNumber) {
            int number = res.number;
            if (number == 0) {
                //neutral operation, nothing to do
                return gates;
            }
            if (number < 0) {
                //if we substract a negative number we can just use plusAssign
                ExpressionResult negative = new ExpressionResult(-number);
                return plusAssign(signalExp, negative);
            }
        }
        //apart from the handling of negative or 0 numbers we can just use plusAssign and reverse the result
        gates = plusAssign(signalExp, res);
        Collections.reverse(gates);
        return gates;
    }

    public static ArrayList<Gate> plus(ExpressionResult firstExp, ExpressionResult secondExp, SignalExpression additionalLines) {
        ArrayList<Gate> gates = new ArrayList<>();
        if (firstExp.isNumber || secondExp.isNumber) {
            //both cant be a number because else the result would be handled by the AST
            int number = numberNotRes(firstExp, secondExp);
            firstExp = resNotNumber(firstExp, secondExp);
            if (number == 0) {
                //neutral operation, just copy Exp to lines
                for (int i = 0; i < firstExp.getWidth(); i++) {
                    Gate tempGate;
                    tempGate = new Gate(Toffoli);
                    tempGate.addTargetLine(additionalLines.getLineName(i));
                    tempGate.addControlLine(firstExp.getLineName(i));
                    gates.add(tempGate);
                }
                return gates;
            }
            if (number < 0) {
                //if we add a negative number we can just use minus
                //because its a number we dont need the twosComplementLine
                ExpressionResult negative = new ExpressionResult(-number);
                return minus(firstExp, negative, additionalLines, null);
            }
            ArrayList<Boolean> numBool = intToBool(number);
            while (numBool.size() < firstExp.getWidth()) {
                numBool.add(false);
            }
            ArrayList<Boolean> numBoolCopy = new ArrayList<>(numBool);
            //we now have firstExp as arbitrary Expression and a BooleanList for the number
            //general case, both are lines
            for (int i = 0; i < firstExp.getWidth(); i++) {
                //Write x to additional line
                Gate tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(additionalLines.getLineName(i));
                tempGate.addControlLine(firstExp.getLineName(i));
                gates.add(tempGate);

            }
            for (int i = 1; i < numBool.size(); i++) {
                //xn ^= yn
                if (numBool.get(i)) {
                    Gate tempGate = new Gate(Toffoli);
                    tempGate.addTargetLine(additionalLines.getLineName(i));
                    gates.add(tempGate);
                }
            }
            for (int i = numBool.size() - 1; i > 1; i--) {
                //yn ^= yn-1 till y2 ^= y1
                numBool.set(i, numBool.get(i) ^ numBool.get(i - 1));
            }

            for (int i = numBool.size() - 1; i > 0; i--) {
                //yn ^= xn-1 & yn-1 and xn ^= yn but we cant write to yn so we do it directly
                //ax -> a1
                for (int j = i; j >= 0; j--) {
                    //bx->b0
                    if (numBool.get(j)) {
                        Gate tempGate = new Gate(Toffoli);
                        tempGate.addTargetLine(additionalLines.getLineName(i));
                        for (int k = i; k >= j; k--) {
                            tempGate.addControlLine(firstExp.getLineName(k));
                        }
                        gates.add(tempGate);
                    }

                }
            }


            for (int i = 0; i < numBoolCopy.size(); i++) {
                //yn ^= x1n
                if (numBoolCopy.get(i)) {
                    Gate tempGate = new Gate(Toffoli);
                    tempGate.addTargetLine(additionalLines.getLineName(i));
                    gates.add(tempGate);
                }
            }

        } else {
            //general case, both are lines
            for (int i = 0; i < firstExp.getWidth(); i++) {
                //Write x to additional line
                Gate tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(additionalLines.getLineName(i));
                tempGate.addControlLine(firstExp.getLineName(i));
                gates.add(tempGate);

            }
            for (int i = 1; i < secondExp.getWidth(); i++) {
                //xn ^= yn
                Gate tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(additionalLines.getLineName(i));
                tempGate.addControlLine(secondExp.getLineName(i));
                gates.add(tempGate);

            }
            for (int i = secondExp.getWidth() - 1; i > 1; i--) {
                //yn ^= yn-1 till y2 ^= y1
                Gate tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(secondExp.getLineName(i));
                tempGate.addControlLine(secondExp.getLineName(i - 1));
                gates.add(tempGate);
            }
            for (int i = 1; i < firstExp.getWidth() && i < secondExp.getWidth(); i++) {
                //yn ^= xn-1 & yn-1
                Gate tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(secondExp.getLineName(i));
                tempGate.addControlLine(additionalLines.getLineName(i - 1));
                tempGate.addControlLine(secondExp.getLineName(i - 1));
                gates.add(tempGate);
            }
            for (int i = Math.min(firstExp.getWidth(), secondExp.getWidth()) - 1; i > 0; i--) {
                //xn ^= yn
                Gate tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(additionalLines.getLineName(i));
                tempGate.addControlLine(secondExp.getLineName(i));
                gates.add(tempGate);
                //reversal of yn ^= xn-1 & yn-1
                tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(secondExp.getLineName(i));
                tempGate.addControlLine(additionalLines.getLineName(i - 1));
                tempGate.addControlLine(secondExp.getLineName(i - 1));
                gates.add(tempGate);
            }
            for (int i = 2; i < secondExp.getWidth(); i++) {
                //reversal of yn ^= yn-1 till y2 ^= y1
                Gate tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(secondExp.getLineName(i));
                tempGate.addControlLine(secondExp.getLineName(i - 1));
                gates.add(tempGate);
            }
            for (int i = 0; i < secondExp.getWidth(); i++) {
                //yn ^= x1n
                Gate tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(additionalLines.getLineName(i));
                tempGate.addControlLine(secondExp.getLineName(i));
                gates.add(tempGate);

            }
        }

        return gates;
    }

    public static ArrayList<Gate> minus(ExpressionResult firstExp, ExpressionResult secondExp, SignalExpression additionalLines, SignalExpression twosComplementLines) {
        //The twosComplementLines is null if one of the numbers is a number
        ArrayList<Gate> gates = new ArrayList<>();
        if (firstExp.isNumber || secondExp.isNumber) {
            //both cant be a number because else the result would be handled by the AST
            int number = numberNotRes(firstExp, secondExp);
            firstExp = resNotNumber(firstExp, secondExp);
            if (number == 0) {
                //neutral operation, just copy Exp to lines
                for (int i = 0; i < firstExp.getWidth(); i++) {
                    Gate tempGate;
                    tempGate = new Gate(Toffoli);
                    tempGate.addTargetLine(additionalLines.getLineName(i));
                    tempGate.addControlLine(firstExp.getLineName(i));
                    gates.add(tempGate);
                }
                return gates;
            }
            if (number < 0) {
                //if we substract a negative number we can just use plus
                ExpressionResult negative = new ExpressionResult(-number);
                return plus(firstExp, negative, additionalLines);
            }
            //create twos complement of number
            number = ~number;
            number = (number & (int) Math.pow(2, firstExp.getWidth()) - 1); //drop all unneeded ones
            number++;
            return plus(firstExp, secondExp, additionalLines);
        } else {
            //second expression is also an expression and not a number
            //do twosComplement on the SignalLine
            ExpressionResult twosComplementRes = new ExpressionResult(twosComplementLines);
            twosComplementRes.gates.addAll(notExp(secondExp, twosComplementRes.signal));
            twosComplementRes.gates.addAll(increment(twosComplementRes.signal));
            gates.addAll(twosComplementRes.gates);
            gates.addAll(plus(firstExp, twosComplementRes, additionalLines));
            return gates;
        }
    }

    public static ArrayList<Gate> bitwiseAnd(ExpressionResult firstExp, ExpressionResult secondExp, SignalExpression additionalLines) {
        ArrayList<Gate> gates = new ArrayList<>();
        if (firstExp.isNumber || secondExp.isNumber) {
            //both cant be a number because else the result would be handled by the AST
            int number = numberNotRes(firstExp, secondExp);
            firstExp = resNotNumber(firstExp, secondExp);
            if (number == 0) {
                //neutral operation, empty gate list
                return gates;
            }
            if (number < 0) {
                //changes negative number to its positive representation with the given lines
                int range = (int) Math.pow(2, firstExp.getWidth());  //so for a 5bit number this would be 32
                number = range + (number % range);    //if the number is bigger than the range we can ignore all other bits
            }
            ArrayList<Boolean> numBool = intToBool(number);
            //we now have firstExp as arbitrary Expression and a BooleanList for the number
            for (int i = 0; i < firstExp.getWidth() && i < numBool.size(); i++) {
                Gate tempGate;
                if (numBool.get(i)) {
                    tempGate = new Gate(Toffoli);
                    tempGate.addTargetLine(additionalLines.getLineName(i));
                    tempGate.addControlLine(firstExp.getLineName(i));
                    gates.add(tempGate);
                }
            }
        } else {
            for (int i = 0; i < firstExp.getWidth() && i < secondExp.getWidth(); i++) {
                Gate tempGate = new Gate(Toffoli);
                tempGate.addTargetLine(additionalLines.getLineName(i));
                tempGate.addControlLine(firstExp.getLineName(i));
                tempGate.addControlLine(secondExp.getLineName(i));
                gates.add(tempGate);
            }
        }
        return gates;
    }

    public static ArrayList<Gate> xor(ExpressionResult firstExp, ExpressionResult secondExp, SignalExpression xorLines) {
        ArrayList<Gate> gates = new ArrayList<>();
        if (firstExp.isNumber || secondExp.isNumber) {
            //both cant be a number because else the result would be handled by the AST
            int number = numberNotRes(firstExp, secondExp);
            firstExp = resNotNumber(firstExp, secondExp);
            if (number == 0) {
                //neutral operation, empty gate list
                return gates;
            }
            if (number < 0) {
                //changes negative number to its positive representation with the given lines
                int range = (int) Math.pow(2, firstExp.getWidth());  //so for a 5bit number this would be 32
                number = range + (number % range);    //if the number is bigger than the range we can ignore all other bits
            }
            ArrayList<Boolean> numBool = intToBool(number);
            //we now have firstExp as arbitrary Expression and a BooleanList for the number
            for (int i = 0; i < firstExp.getWidth() || i < numBool.size(); i++) {
                Gate tempGate;
                if (i < firstExp.getWidth()) {
                    tempGate = new Gate(Toffoli);
                    tempGate.addTargetLine(xorLines.getLineName(i));
                    tempGate.addControlLine(firstExp.getLineName(i));
                    gates.add(tempGate);
                }
                if (i < numBool.size() && numBool.get(i)) {
                    tempGate = new Gate(Toffoli);
                    tempGate.addTargetLine(xorLines.getLineName(i));
                    gates.add(tempGate);
                }
            }
        } else {
            for (int i = 0; i < firstExp.getWidth() || i < secondExp.getWidth(); i++) {
                if (i < firstExp.getWidth()) {
                    Gate tempGate = new Gate(Toffoli);
                    tempGate.addTargetLine(xorLines.getLineName(i));
                    tempGate.addControlLine(firstExp.getLineName(i));
                    gates.add(tempGate);
                }
                if (i < secondExp.getWidth()) {
                    Gate tempGate = new Gate(Toffoli);
                    tempGate.addTargetLine(xorLines.getLineName(i));
                    tempGate.addControlLine(secondExp.getLineName(i));
                    gates.add(tempGate);
                }
            }
        }
        return gates;
    }

    private static ArrayList<Boolean> intToBool(int num) {
        ArrayList<Boolean> booleans = new ArrayList<>();
        for (int i = num; i > 0; i /= 2) {
            booleans.add(i % 2 == 1);
        }
        return booleans;
    }

    private static ExpressionResult resNotNumber(ExpressionResult firstExp, ExpressionResult secondExp) {
        //returns the first ExpressionResult that is not a number
        if (!firstExp.isNumber) return firstExp;
        if (!secondExp.isNumber) return secondExp;
        return null;
    }

    private static Integer numberNotRes(ExpressionResult firstExp, ExpressionResult secondExp) {
        //returns the first ExpressionResult that is a number as Integer
        if (firstExp.isNumber) return firstExp.number;
        if (secondExp.isNumber) return secondExp.number;
        return null;
    }

    public static ArrayList<Gate> reverseGates(ArrayList<Gate> gates) {
        ArrayList<Gate> reverse = new ArrayList<>();
        for (Gate gate : gates) {
            Gate tempGate = new Gate(gate.kind);
            tempGate.addTargetLines(gate.getTargetLines());
            tempGate.addControlLines(gate.getControlLines());
            reverse.add(tempGate);
        }
        Collections.reverse(reverse);
        return reverse;
    }
}
