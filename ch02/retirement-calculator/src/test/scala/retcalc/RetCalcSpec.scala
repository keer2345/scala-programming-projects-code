package retcalc

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalactic.{TypeCheckedTripleEquals, Equality, TolerantNumerics}

class RetCalcSpec
    extends AnyWordSpec
    with Matchers
    with TypeCheckedTripleEquals {

  implicit val doubleEquality: Equality[Double] =
    TolerantNumerics.tolerantDoubleEquality(0.0001)

  "RetCalc" when {
    "futureCapital" should {
      "calculate the amount of savings I will have in n months" in {
        val actual = RetCalc.futureCapital(
          interestRate = 0.04 / 12,
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
