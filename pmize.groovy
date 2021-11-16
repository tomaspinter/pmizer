// reads json from this file in same folder
def raw = new File('src.json').text
def slurp = new groovy.json.JsonSlurper()
def j = slurp.parseText(raw)

// stores output in this file
out = new File('otpt.js')
scrpt = ''
lets = []
indent = ''

def tabUp()    { indent += '  ' }
def tabDown()  { indent -= '  ' }

def isList(v) {
    return v instanceof List
}

def isString(v) {
    return v instanceof String || v instanceof Number
}

def expecter(obj, prop, val) {
    "${indent}pm.expect(${obj}.${prop}).to.eql($val);\n"
}

def listExpecter(obj, prop, val) {
    "${indent}pm.expect(${obj}.${prop}).to.have.members($val);\n"
}

def turnToPm (obj, prop, val) {
    def v = isNumeric(val) || val == true || val == false ? val : "'" + val + "'"
    expecter(obj, prop, v)
}

// numeric references are usually 4+ digit long integers
def isNumeric (val) {
    val ==~ /\d{1,4}|\d+\.\d+/
}

def checkLets (pmVar, separator = '') {
    def v = pmVar
    def c = -1
    while (v in lets) {
        c++
        v = "${pmVar}${separator}${c}"
        println v
    }
    lets << v
    v
}

def checkValue (pmVar, slurp) {
    def curPmVar = pmVar
    def newPmVar

    slurp.each { k,v ->

        if (isString(v) || v == true || v == false) scrpt += turnToPm(curPmVar, k, v)

        else if (isList(v)) {
            newPmVar = k
            def c = -1
            tabUp()
            if (isString(v[0])) {
                tabDown()
                scrpt += listExpecter(curPmVar, newPmVar, v.collect {"'$it'"}.toString())
                tabUp()
            }
            else {
                v.each {
                    obj -> {
                        c++
                        def okPmVar = checkLets("${newPmVar}_${c}", '_')
                        scrpt += "\n${indent}let $okPmVar = ${curPmVar}.${newPmVar}[${c}];\n\n"
                        checkValue(okPmVar, obj)
                    }
                }
            }
            tabDown()
        }

        else {
            tabUp()
            def okVar = checkLets(k)
            newPmVar = k
            scrpt += "\n${indent}let $okVar = ${curPmVar}.${newPmVar};\n\n"
            checkValue(okVar, v)
            tabDown()
        }

    }

    scrpt += "${indent}//end of $curPmVar\n"
}

def pmize(slurp) {
    def pmVar = 'r'
    def scr = scrpt
    scrpt += "pm.response.to.have.status(200)\n\n"
    scrpt += "let $pmVar = pm.response.json();\n\n"
    checkValue(pmVar, slurp)
    out.write(scrpt)
}

pmize(j)
