**Chapter 02 Developing a Retirement Calculator**

# Project overview

使用参数来表示 _净收入、开销_ 以及 _原始资本_ 等等。我们创建函数来进行如下计算：

- 退休时的未来资金
- 退休若干年后的资金
- 需要存多久才能退休

我们首先使用固定利率来进行这些运算。然后从 `.tsv` 文件加载数据，重构之前的函数来模拟退休时的情景。

# Calculating the future capital

首先需要了解，在你选择的退休日期能获得多少资金。当下，我们假定你每月以固定利率投资存款，简单来说我们忽略通货膨胀的影响。
因此，资本计算将以今天的货币和利率来计算：_real rate = nominal interest rate - inflation rate_
（实际利率 = 名义利率 - 通货膨胀利率）。

我们不打算在本章提及货币。你可能考虑 USD, EUR 或者其他货币，只要所有计算都用同一种货币就不会影响计算结果。

## Writing a unit test for the accumulation phase

我们需要类似 Excel 中的 `FV` 的函数：它根据固定利率计算投资的未来价值。基于 TDD 驱动，首先创建一个失败的测试：

1. 创建 `retirement_calcular` 项目。
1. 创建 `retcalc` 包。
1. 创建 `RetCalcSpec` 测试类。

```
sbt new scala/scala-seed.g8
[warn] insecure HTTP request is deprecated 'http://maven.aliyun.com/nexus/content/groups/public/'; switch to HTTPS or opt-in as ("aliyun" at "http://maven.aliyun.com/nexus/content/groups/public/").withAllowInsecureProtocol(true)
[warn] insecure HTTP request is deprecated 'http://repo1.maven.org/maven2/'; switch to HTTPS or opt-in as ("central" at "http://repo1.maven.org/maven2/").withAllowInsecureProtocol(true)

A minimal Scala project.

name [Scala Seed Project]: retirement calculator

Template applied in ~/retirement-calculator
```

```
cd retirement-calculator
mkdir -p src/main/scala/retcalc
mkdir -p src/test/scala/retcalc
touch src/test/scala/retcalc/RetCalcSpec.scala
```

代码如下：
```scala
package retcalc

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalactic.{TypeCheckedTripleEquals, Equality, TolerantNumerics}

class RetCalcSpec
    extends AnyWordSpec
    with Matchers
    with TypeCheckedTripleEquals {
  "RetCalc" when {
    "futureCapital" should {
      "calculate the amount of savings I will have in n months" in {
        implicit val doubleEquality: Equality[Double] =
          TolerantNumerics.tolerantDoubleEquality(0.0001)
        val actual = RecCalc.futureCapital(
          interestRate = 0.04 / 12,
          nbOfMonths = 25 * 12,
          netIncome = 3000,
          currentExpenses = 2000,
          initialCapital = 10000
        )
        val expected = 541267.1990
        actual should equal(expected)
      }
    }
  }
}
```

我们使用了 `TypeCheckedTripleEquals`，它有强大的断言处理，`should ===` 确保编译时两边值和类型相等，默认的 ScalaTest 断言 `should` 在运行时验证类型是否相等。推荐使用 `should ===`，这样可以在重构代码时节约很多时间。

另外，它允许我们在比较 double 值时有一定的容错（tolerance），看看下面的声明：
```scala
implicit val doubleEquality: Equality[Double] =
  TolerantNumerics.tolerantDoubleEquality(0.0001)
```

这样在比较 `double1 should ===(double2)` 时，如果两者误差的绝对值小于 `0.0001` 则视为允许的误差。例如：
```scala
scala> val double1 = 0.01 - 0.001 + 0.001
val double1: Double = 0.010000000000000002

scala> double1 == 0.01
val res0: Boolean = false
```
有些奇怪，但众所周知是在任何语言中都有的以二进制编码的浮点数的问题。我们使用 `BigDecimal` 来取代 `Double` 就可以避免这样的问题，但是我们不需要这么精确的判断，并且会降低效率。

这段代码非常直观，调用函数与期待的值做比较，类似 Excel 中的 `FV(0.04/12, 25*12, 1000, 10000,0)`，我们假设用户每月将其收入和支出之间的差额存起来，那么支付款参数在 `FV` 函数中是 `1,000 = netIncome - currentExpense`。

我们测试失败，是因为还没有 `RetCalc` 对象和 `futureCapital` 函数，我们来创建它：
```
touch src/main/scala/retcalc/RetCalc.scala
```
```scala
package retcalc

object RetCalc {
  def futureCapital(
      interestRate: Double,
      nbOfMonths: Int,
      netIncome: Int,
      currentExpenses: Int,
      initialCapital: Double
  ): Double = ???
}
```

## Implementing futureCapital

我们的测试还是失败了，因为还没有给函数 `futureCaptial` 实现代码。如果使用 `initialCapital = 10,000` 和 `monthlySavings = 1,000` 来计算，我们需要执行：
1. 第 0 个月，开始存钱之前：`capital0 = initialCapital = 10,000` 。
1. 第 1 个月，我们又初始资本和 `1,000` 元存款，所以资本为 `capital1 = capital0 *(1 + monthlyInterestRate) + 1,000`。
1. 第 2 个月，`capital2 = capital1 * (1 + monthlyInterestRate) + 1,000`。

```scala
  def futureCapital(
      interestRate: Double,
      nbOfMonths: Int,
      netIncome: Int,
      currentExpenses: Int,
      initialCapital: Double
  ): Double = {
    val monthlySavings = netIncome - currentExpenses
    def nextCapital(accumulated: Double, month: Int): Double =
      accumulated * (1 + interestRate) + monthlySavings

    (0 until nbOfMonths).foldLeft(initialCapital)(nextCapital)

    // def foldLeft[B](z: B)(op: (B, A) => B): B
    //
    // 0: initialCapital
    // 1: initialCapital * (1 + interestRate) + monthlySaving
    // 2: [1] * (1+interestRate) + monthlySaving
    // 3: [2] * (1+interestRate) + monthlySaving
    // ...
  }
```

`foldLeft` 具体是 
```scala
def foldLeft[B](z: B)(op: (B, A) => B): B
```

- `[B]` 意思是函数的参数类型为 `B`，当调用函数，编译器自动指向 `B`，依据 `z: B` 的参数。在上面的代码中，`z` 参数是 `initialCapital`，为 `Double` 类型。因此，我们调用 `foldLeft` 来计算 `futureCapital`，这里 `B = Double`：
```scala
def foldLeft(z: Double)(op: (Double, A) => Double): Double
```

- 函数有两个参数。Scala 允许有多个参数列表。每个列表可以有一个或者多个参数。
- `op: (B, A) => B` 意思是 `op` 必须是有类型分别为 `B` 和 `A` 的两个参数，并且返回值类型为 `B`。由于 `foldLeft` 函数调用了另一个函数作为参数，我们称 `foldLeft` 为高阶函数（**higer order function**）。

到这里，我们再测试一下，就发现测试通过了：
```scala
> sbt test
[info] RetCalcSpec:
[info] RetCalc
[info]   when futureCapital
[info]   - should calculate the amount of savings I will have in n months
[info] HelloSpec:
[info] The Hello object
[info] - should say hello
[info] Run completed in 536 milliseconds.
[info] Total number of tests run: 2
[info] Suites: completed 2, aborted 0
[info] Tests: succeeded 2, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

## Refactoring the production code

在 TDD 开发模式里，测试通过后一般都会重构代码。如果测试覆盖率良好，就不用担心修改代码，因为任何错误都应该由测试来标记。这就是所谓的红绿重构（**Red-Green-Refactor**）周期。

`futureCapital` 代码改变如下：
```scala
  def futureCapital(
      interestRate: Double,
      nbOfMonths: Int,
      netIncome: Int,
      currentExpenses: Int,
      initialCapital: Double
  ): Double = {
    val monthlySavings = netIncome - currentExpenses

    (0 until nbOfMonths).foldLeft(initialCapital)((accumulated, _) =>
      accumulated * (1 + interestRate) + monthlySavings
    )
  }
```
这是个在 `foldLeft` 调用内联的 `nextCapital` 函数。在 Scala 中，我们可以这样定义匿名函数（**anonymous function**）：
```scala
(param1, param2, ..., paramN) => function body
```

我们看到参数 `month` 并没有在 `nextCapital` 中使用。在匿名函数里，将不是用的参数用 `_` 表示。参数 `_` 不能用于函数体，如果尝试将其替换成有意义的参数名，类似 Intellij IDEA 将会标出下划线，提示该参数是 `Declaration is never used`。

## Writing a test for the decumulation phase
到目前已经知道退休时期望的资金是多少了，是时候将 `futureCapital` 函数用于计算你的技能人能有多少资金了。

添加如下测试到 `RetCalcSpec`，在之前的测试单元下方，并运行它，应该能通过：
```scala
class RetCalcSpec
    extends AnyWordSpec
    with Matchers
    with TypeCheckedTripleEquals {

  implicit val doubleEquality: Equality[Double] =
    TolerantNumerics.tolerantDoubleEquality(0.0001)

  "RetCalc" when {
    "futureCapital" should {
      "calculate the amount of savings I will have in n months" in {
        // ...
      }

      "calculate how much savings will be left after having taken a pernsion for n months" in {
        val actual = RetCalc.futureCapital(
          interestRate = 0.04 / 12,
          nbOfMonths = 40 * 12,
          netIncome = 0,
          currentExpenses = 2000,
          initialCapital = 541267.1990
        )
        val expected = 309867.53176
        actual should ===(expected)
      }
    }
  }
}
```
如果在退休后的 40 年，每个月的支出相同并且没有任何收入的话，你将有一笔资金留给继承人。如果资金是负数，就意味着你在退休的某个时候把钱花光了，这是我们想要避免的结果。

可以从 Scala 控制台根据自身情况来尝试不同的结果，通过不同的利率来观察资金变成负数的情况。

请注意，在生产系统中，您肯定会添加更多单元测试来覆盖其他一些边缘情况，并确保函数不会崩溃。由于我们将在第 3 章”处理错误“中介绍错误处理，我们可以假设 `futureCapital` 的测试覆盖率目前已经足够好了。

## Simulating a retirement plan

既然我们知道了如何计算退休时和死后的资本，那么将这两个调用合并到一个函数中会很有用。此函数将一次性模拟退休计划。

`RetCalcSpec.scala`:
```scala
  "RetCalc" when {
    "simulatePlan" should {
      "calculate the capital at retirement and the capital after death" in {
        val (capitalAtRetirement, capitalAfterDeath) = RetCalc.simulatePlan(
          interestRate = 0.04 / 12,
          nbOfMonthsSaving = 25 * 12,
          nbOfMonthsInRetirement = 40 * 12,
          netIncome = 3000,
          currentExpenses = 2000,
          initialCapital = 10000
        )
        capitalAtRetirement should ===(541267.1990)
        capitalAfterDeath should ===(309867.5316)

      }
    }
  }
```
`RetCalc.scala`:
```scala
  def simulatePlan(
      interestRate: Double,
      nbOfMonthsSaving: Int,
      nbOfMonthsInRetirement: Int,
      netIncome: Int,
      currentExpenses: Int,
      initialCapital: Double
  ): Double = ???
```

这这时候测试也是失败的，函数 `simulatePlan` 必须返回两个值，最简单的方式是使用 `Tuple2`，在 Scala 中元组是不可变的数据结构并支持多种不同类型的对象。这与 `case class` 相似，`case class` 的属性特定名称。我们称 `tuple` 或 `case class` 为生产类型。

```scala
scala> val tuple3 = (1, "hello", 2.0)
val tuple3: (Int, String, Double) = (1,hello,2.0)

scala> tuple3._1
val res0: Int = 1

scala> tuple3._2
val res1: String = hello

scala> val (a, b, c) = tuple3
val a: Int = 1
val b: String = hello
val c: Double = 2.0

scala> a
val res2: Int = 1

scala> c
val res3: Double = 2.0
```

元组最大长度为 22，访问元素可以通过 `_1`，`_2` 的形式。我们可以为元组的每个元素一次声明多个变量。

```scala
  def simulatePlan(
      interestRate: Double,
      nbOfMonthsSaving: Int,
      nbOfMonthsInRetirement: Int,
      netIncome: Int,
      currentExpenses: Int,
      initialCapital: Double
  ): (Double, Double) = {
    val capitalAtRetirement = futureCapital(
      interestRate = interestRate,
      nbOfMonths = nbOfMonthsSaving,
      netIncome = netIncome,
      currentExpenses = currentExpenses,
      initialCapital = initialCapital
    )
    val capitalAfterDeath = futureCapital(
      interestRate = initialCapital,
      nbOfMonths = nbOfMonthsInRetirement,
      netIncome = 0,
      currentExpenses = currentExpenses,
      initialCapital = capitalAtRetirement
    )

    (capitalAtRetirement, capitalAfterDeath)
  }
```

再测试 `RetCalaSpec`，看到测试通过了。

# Calculating when you can retire
如果尝试从 Scala Console 调用 `simulatePlan`，你可能尝试了不同的 `nbOfMonths` 值来观察退休时和死亡时的资金。拥有一个查找最优的 `nbOfMonths` 的函数是很有必要的，以便于有足够的资金并且在退休的日子里花不完。


```scala
    "nbOfMonthsSaving" should {
      "calculate how long I need to save before I can retire" in {
        val actual = RetCalc.nbOfMonthsSaving(
          interestRate = 0.04 / 12,
          nbOfMonthsInRetirement = 40 * 12,
          netIncome = 3000,
          currentExpenses = 2000,
          initialCapital = 10000
        )
        val expected = 23 * 12 + 1
        actual should ===(expected)
      }
    }
```
## Writing the function body

```scala
  def nbOfMonthsSaving(
      interestRate: Double,
      nbOfMonthsInRetirement: Int,
      netIncome: Int,
      currentExpenses: Int,
      initialCapital: Double
  ): Int = {
    def loop(months: Int): Int = {
      val (capitalAtRetirement, capitalAfterDeath) = simulatePlan(
        interestRate = interestRate,
        nbOfMonthsSaving = months,
        nbOfMonthsInRetirement = nbOfMonthsInRetirement,
        netIncome = netIncome,
        currentExpenses = currentExpenses,
        initialCapital = initialCapital
      )
      val returnValue = if (capitalAfterDeath > 0) months else loop(months + 1)
      returnValue
    }
    loop(0)
  }
```

我们声明了一个递归函数，


## Understanding tail-recursion
继续新的测试：
```scala
    "nbOfMonthsSaving" should {
      "calculate how long I need to save before I can retire" in {
      // ...
      }
      "not crash if the resulting nbOfMonths is very high" in {
        val actual = RetCalc.nbOfMonthsSaving(
          interestRate = 0.01 / 12,
          nbOfMonthsInRetirement = 40 * 12,
          netIncome = 3000,
          currentExpenses = 2999,
          initialCapital = 0
        )
        val expected = 8280
        actual should ===(expected)
      }
    }
```
```scala
package retcalc

import scala.annotation.tailrec

// ...

  def nbOfMonthsSaving(
      interestRate: Double,
      nbOfMonthsInRetirement: Int,
      netIncome: Int,
      currentExpenses: Int,
      initialCapital: Double
  ): Int = {
    @tailrec
    def loop(months: Int): Int = {
      val (capitalAtRetirement, capitalAfterDeath) = simulatePlan(
        interestRate = interestRate,
        nbOfMonthsSaving = months,
        nbOfMonthsInRetirement = nbOfMonthsInRetirement,
        netIncome = netIncome,
        currentExpenses = currentExpenses,
        initialCapital = initialCapital
      )
      //   val returnValue = if (capitalAfterDeath > 0) months else loop(months + 1)
      //   returnValue
      if (capitalAfterDeath > 0.0) months else loop(months + 1)
    }
    // loop(0)
    if (netIncome > currentExpenses) loop(0) else Int.MaxValue
  }
```

上面在这段代码使用了**尾部递归**（tail-recursive）。通常来说，超过 100 次的递归都应该使用尾部递归，否则会报堆栈溢出错误（`StackOverflowError`）。尾部递归使用 `@tailrec` 注解可以让编译器校验它是否是尾部递归。

## Ensuring termination

我们的代码还没有完成，因为函数可能无限循环。假设你花的比挣得多，将永远不能存够退休：
```scala
    "not loop forever if I enter bad parameters" in {
      val actual = RetCalc.nbOfMonthsSaving(
        interestRate = 0.04 / 12,
        nbOfMonthsInRetirement = 40 * 12,
        netIncome = 1000,
        currentExpenses = 2000,
        initialCapital = 10000
      )
      actual should ===(Int.MaxValue)
    }
```
# Using market rates
在我们的计算中，一直假设利率是一个常量，但实际上复杂得多。用市场数据中的实际利率来对我们的退休计划获得更多信心，这将更为准确。为此，我们手下你需要修改代码，以便使用可变的利率执行相同的计算。之后，我们将加载真实的市场数据，通过跟踪标准普尔 500 指数来模拟基金的常规投资。

## Defining an algebraic data type

为了支持可变利率，我们需要改变含有入参 `interestRate: Double` 的所有函数。我们需要一个可以表示常量利率或义序列利率的类型。

考虑到 `A` 和 `B` 两种类型，我们之前知道了如何定义类型 `A` sum 类型 `B`，这是一个生产类型，我们可以使用元组来定义，例如 `ab: (A, B)`，或者 `case class MyProduct(a:A, b: B)`。

换而言之，可以包含 `A` 或 `B` 的合计类型，在 Scala 中，我们使用 `sealed` trait 来继承:
```scala
sealed trait Shape
case class Circle(diameter: Double) extends Shape
case class Rectangle(width: Double, height: Double) extends Shapte
```

代数数据类型（Algebraic Data Type - ADT）是结合 sum types 和 product types 来定义的数据结构。刚才我们定义了 `Shape` 代数数据类型。

`sealed` 关键字表示所有子类必须与 trait 声明在同一个 `.scala` 文件。如果尝试在其他文件声明一个类来继承 `sealed` 特质，编译器将拒绝。

回到我们的问题，我们可以定义一个 `Returns` ADT，在 `src/main/scala` 的 `retcalc` 包创建一个新的 Scala 类：

```scala
package retcalc

sealed trait Returns

case class FixedReturns(annualRate: Double) extends Returns
case class VariableReturns(returns: Vector[VariableReturn]) extends Returns

case class VariableReturn(monthId: String, monthlyRate: Double)
```
对于 `VariableReturn`，我们保留月利率和标识 `monthId` 为 $2017.02$，即 *February 2017*。推荐使用 `Vector` 来构件元素的序列模型，`Vector` 在 appending/inserting  和 通过索引访问元素封面比 `List` 快速。

## Filtering returns for a specific period

当我们在很长一段时间内（例如 1900 年至 2017 年）有可变回报（`VariableReturns`）时，使用一个较小的周期来模拟如果在较短时期（比如 50 年）内的历史回报率将重复发生，将会发生什么。

我们在 `VariableReturns` 类中创建方法来确保汇报只包含特定的时。下面是 `ReturnsSpec.scala` 测试单元：


```scala
package retcalc

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalactic.TypeCheckedTripleEquals
import org.scalactic.Equality
import org.scalactic.TolerantNumerics

class ReturnsSpec
    extends AnyWordSpec
    with Matchers
    with TypeCheckedTripleEquals {
  implicit val doubleEquality: Equality[Double] =
    TolerantNumerics.tolerantDoubleEquality(0.0001)
  "VariableReturns" when {
    "formUntil" should {
      "keep only a window of the returns" in {
        val variableReturns = VariableReturns(Vector.tabulate(12) { i =>
          val d = (i + 1).toDouble
          VariableReturn(f"2017.$d%02.0f", d)
        })

        variableReturns.fromUntil("2017.07", "2017.09").returns should ===(
          Vector(VariableReturn("2017.07", 7.0), VariableReturn("2017.08", 8.0))
        )

        variableReturns.fromUntil("2017.10", "2018.01").returns should ===(
          Vector(
            VariableReturn("2017.10", 10.0),
            VariableReturn("2017.11", 11.0),
            VariableReturn("2017.12", 12.0)
          )
        )
      }
    }
  }
}
```
首先，我们创建一个有 12 个元素的序列，从 `2017.01` 到 `2017.12`。用函数 `fromUntil`来指定开始年月和终止年月。

完善 `VariableReturns` 类：

```scala
case class VariableReturns(returns: Vector[VariableReturn]) extends Returns {
  def fromUntil(monthIdFrom: String, monthIdUntils: String): VariableReturns =
    VariableReturns(
      returns
        .dropWhile(_.monthId != monthIdFrom)
        .takeWhile(_.monthId != monthIdUntils)
    )
}
```

## Pattern Matching

现在，我们有了多种返回值，需要修改 `futureCapital` 函数来接受 `Returns` 类型，替换 `Double` 类型的月利率：
```scala
"RetCalc" when {
  "futureCapital" should {
    "calculate the amount of savings I will have in n months" in {
      // Excel = -FV(0.04/12, 25*12, 1000, 10000)

      val actual = RetCalc.futureCapital(
        //   interestRate = 0.04 / 12,
        FixedReturns(0.04),
        nbOfMonths = 25 * 12,
        netIncome = 3000,
        currentExpenses = 2000,
        initialCapital = 10000
      )

      val expected = 541267.1990
      actual should ===(expected)
    }

    "calculate how much savings will be left after having taken a pernsion for n months" in {
      val actual = RetCalc.futureCapital(
        // interestRate = 0.04 / 12,
        FixedReturns(0.04),
        nbOfMonths = 40 * 12,
        netIncome = 0,
        currentExpenses = 2000,
        initialCapital = 541267.1990
      )
      val expected = 309867.53176
      actual should ===(expected)
    }
  }
}
```
相应的，`RetCalc.futureCapital` 也做修改：
```scala

  def futureCapital(
      // interestRate: Double,
      returns: Returns,
      nbOfMonths: Int,
      netIncome: Int,
      currentExpenses: Int,
      initialCapital: Double
  ): Double = {
    val monthlySavings = netIncome - currentExpenses

    (0 until nbOfMonths).foldLeft(initialCapital) {
      case (accumulated, month) =>
        accumulated * (1 + Returns.monthlyRate(returns, month)) + monthlySavings
    }
  }
```

创建测试单元 `src/test/scala/retcalc/ReturnsSpec.scala`:
```scala
  "Returns.object" when {
    "monthlyRate" should {
      "return a fixed rate for a FixedReturn" in {
        Returns.monthlyRate(FixedReturns(0.04), 0) should ===(0.04 / 12)
        Returns.monthlyRate(FixedReturns(0.04), 9) should ===(0.04 / 12)
      }

      val variableReturns = VariableReturns(Vector(
        VariableReturn("2000.01", 0.1), VariableReturn("2000.02", 0.2)
      ))

      "return the nth rate for VariableReturn" in {
        Returns.monthlyRate(variableReturns,0) should ===(0.1)
        Returns.monthlyRate(variableReturns,1) should ===(0.2)
      }
      "roll over from the first rate if n > length" in {
        Returns.monthlyRate(variableReturns, 2) should ===(0.1)
        Returns.monthlyRate(variableReturns, 3) should ===(0.2)
        Returns.monthlyRate(variableReturns, 4) should ===(0.1)
      }
    }
  }
```

`Returns.scala`:
```scala
object Returns {
  def monthlyRate(returns: Returns, month: Int): Double = returns match {
    case FixedReturns(r) => r / 12
    case VariableReturns(rs) => rs(month % rs.length).monthlyRate
  }
}
```


这是个简单的例子，但我们可以有更复杂的模式。这种特性非常强大，通常替换 `if/else` 表达式。比如：
```scala
scala> Vector(1, 2, 3, 4) match {
     |   case head +: second +: tail => tail
     | }
val res5: scala.collection.immutable.Vector[Int] = Vector(3, 4)

scala> Vector(1, 2, 3, 4) match {
     |   case head +: second +: tail => second
     | }
val res6: Int = 2

scala> ("0", 1, (2.0, 3.0)) match {
     |   case ("0", int, (d0, d1)) => d0 + d1
     | }
val res7: Double = 5.0

scala> "hello" match {
     |   case "hello" | "world" => 1
     |   case "hello world world" => 2
     | }
val res8: Int = 1
```

我是函数式编程的的倡导者，更喜欢在对象红使用纯函数：
- 因为整个调度逻辑在一个地方，所以它们更容易推理。
- 很容易一直到其他对象，有助于重构。
- 它们的范围更为有限。在类方法中，始终在作用域中拥有类的所有属性。在函数中，只有函数的参数。这有助于单元测试和可读性，因为您知道函数除了参数之外不能使用其他任何东西。另外，当类具有可变属性时，它可以避免副作用。
- 有时在面向对象的设计中，当一个方法操作两个对象`A` 和 `B` 时，不清楚该方法应该在类 `A` 还是类 `B` 中。
