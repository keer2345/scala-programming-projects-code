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
