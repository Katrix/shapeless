/*
 * Copyright (c) 2013-16 Miles Sabin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package shapeless

import org.junit.Test
import org.junit.Assert._

import scala.collection.mutable.ListBuffer

import test._

class LazyStrictTests {

  @Test
  def testEffectOrder: Unit = {
    val effects = ListBuffer[Int]()

    implicit def int: Int = { effects += 3; 23 }
    implicit def long: Long = { effects += 6; 23 }

    def summonLazyInt(implicit li: => Int): Int = {
      effects += 2
      val i = li
      effects += 4
      i
    }

    def summonStrictLong(implicit sl: Long): Long = {
      effects += 7
      val i = sl
      effects += 8
      i
    }

    effects += 1
    val li = summonLazyInt
    effects += 5
    val sl = summonStrictLong
    effects += 9

    assertEquals(23, li)
    assertEquals(23, sl)
    assertEquals(1 to 9, effects.toList)
  }

  @Test
  def testDefConversion: Unit = {
    val effects = ListBuffer[Int]()

    def effectfulLazyInt: Int = { effects += 3 ; 23 }

    def useEffectfulLazyInt(li: => Int): Int = {
      effects += 2
      val i = li
      effects += 4
      i
    }

    def effectfulStrictInt: Int = { effects += 6 ; 23 }

    def useEffectfulStrictInt(li: Int): Int = {
      effects += 7
      val i = li
      effects += 8
      i
    }

    effects += 1
    val il = useEffectfulLazyInt(effectfulLazyInt)
    effects += 5
    val is = useEffectfulStrictInt(effectfulStrictInt)
    effects += 9

    assertEquals(23, il)
    assertEquals(23, is)
    assertEquals(1 to 9, effects.toList)
  }

  @Test
  def testLazyConversion: Unit = {
    val effects = ListBuffer[Int]()

    lazy val effectfulLazyInt: Int = { effects += 3 ; 23 }
    lazy val effectfulStrictInt: Int = { effects += 6 ; 23 }

    def useEffectfulLazyInt(li: => Int): Int = {
      effects += 2
      val i = li
      effects += 4
      i
    }

    def useEffectfulStrictInt(li: Int): Int = {
      effects += 7
      val i = li
      effects += 8
      i
    }

    effects += 1
    val il = useEffectfulLazyInt(effectfulLazyInt)
    effects += 5
    val is = useEffectfulStrictInt(effectfulStrictInt)
    effects += 9

    assertEquals(23, il)
    assertEquals(23, is)
    assertEquals(1 to 9, effects.toList)
  }

  @Test
  def testInlineConversion: Unit = {
    val effects = ListBuffer[Int]()

    def useEffectfulLazyInt(li: => Int): Int = {
      effects += 3
      val i = li
      effects += 4
      i
    }

    def useEffectfulStrictInt(si: Int): Int = {
      effects += 7
      val i = si
      effects += 8
      i
    }

    effects += 1
    val il = useEffectfulLazyInt({ effects += 2 ; 23 })
    effects += 5
    val is = useEffectfulStrictInt({ effects += 6 ; 23 })
    effects += 9

    assertEquals(23, il)
    assertEquals(23, is)
    assertEquals(1 to 9, effects.toList)
  }

  sealed trait List[+T]
  case class Cons[T](hd: T, tl: List[T]) extends List[T]
  sealed trait Nil extends List[Nothing]
  case object Nil extends Nil

  trait Show[T] {
    def apply(t: T): String
  }

  def show[T](t: T)(implicit s: Show[T]) = s(t)

  trait CommonShows {
    implicit def showInt: Show[Int] = _.toString
    implicit def showNil: Show[Nil] = _ => "Nil"
  }

  object LazyShows extends CommonShows {
    implicit def showCons[T](implicit st: => Show[T], sl: => Show[List[T]]): Show[Cons[T]] =
      t => s"Cons(${show(t.hd)(st)}, ${show(t.tl)(sl)})"

    implicit def showList[T](implicit sc: => Show[Cons[T]]): Show[List[T]] = {
      case n: Nil => show(n)
      case c: Cons[T] => show(c)(sc)
    }
  }

  object LazyStrictMixShows extends CommonShows {
    implicit def showCons[T](implicit st: => Show[T], sl: => Show[List[T]]): Show[Cons[T]] =
      t => s"Cons(${show(t.hd)(st)}, ${show(t.tl)(sl)})"

    implicit def showList[T](implicit sc: => Show[Cons[T]]): Show[List[T]] = {
      case n: Nil => show(n)
      case c: Cons[T] => show(c)(sc)
    }
  }

  @Test
  def testRecursive: Unit = {
    val l: List[Int] = Cons(1, Cons(2, Cons(3, Nil)))

    val lazyRepr = {
      import LazyShows._
      show(l)
    }

    val strictRepr = {
      import LazyStrictMixShows._
      show(l)
    }

    val expectedRepr = "Cons(1, Cons(2, Cons(3, Nil)))"

    assertEquals(expectedRepr, lazyRepr)
    assertEquals(expectedRepr, strictRepr)
  }

  trait Foo[T]
  object Foo {
    implicit def mkFoo[T]: Foo[T] = new Foo[T] {}
  }

  @Test
  def testMultiple: Unit = {
    val foos = Lazy.values[Foo[Int] :: Foo[String] :: Foo[Boolean] :: HNil]
    implicit val x :: y :: z :: HNil = foos

    typed[Foo[Int]](x)
    typed[Foo[String]](y)
    typed[Foo[Boolean]](z)

    val x1 = implicitly[Foo[Int]]
    val y1 = implicitly[Foo[String]]
    val z1 = implicitly[Foo[Boolean]]

    assertTrue(x1 eq x)
    assertTrue(y1 eq y)
    assertTrue(z1 eq z)
  }

  trait Bar[A] { def foo(a: A): Unit }
  object Bar {
    implicit val intBar = new Bar[Int] { def foo(x: Int) = () }
  }

  @Test
  def testEta: Unit = {
    //implicitly[=> Bar[Int]].value.foo _
    implicitly[Bar[Int]].foo _
    ()
  }

  trait Baz[T] {
    type U
  }

  object Baz {
    def lazyBaz[T, U](t: T)(implicit bt: => Aux[T, U]): Aux[T, U] = bt
    def strictBaz[T, U](t: T)(implicit bt: Aux[T, U]): Aux[T, U] = bt

    type Aux[T, U0] = Baz[T] { type U = U0 }

    implicit val bazIS: Aux[Int, String] = new Baz[Int] { type U = String }
    implicit val bazBD: Aux[Boolean, Double] = new Baz[Boolean] { type U = Double }
  }

  @Test
  def testAux: Unit = {
    val lIS = Baz.lazyBaz(23)
    val sIS = Baz.strictBaz(23)
    typed[Baz.Aux[Int, String]](lIS)
    typed[Baz.Aux[Int, String]](sIS)

    val lBD = Baz.lazyBaz(true)
    val sBD = Baz.strictBaz(true)
    typed[Baz.Aux[Boolean, Double]](lBD)
    typed[Baz.Aux[Boolean, Double]](sBD)
  }

  @Test
  def testExtractors: Unit = {
    //implicitly[=> Generic[Symbol]]
    implicitly[Generic[Symbol]]

    val x = {
      case class Leaf[A](value: A)
      //implicitly[=> Generic[Leaf[Int]]]
      implicitly[Generic[Leaf[Int]]]
      ()
    }
  }

  @Test
  def testNotFound: Unit = {
    @scala.annotation.implicitNotFound("No U[${X}]")
    trait U[X]

    trait V

    @scala.annotation.implicitNotFound("No W[${X}, ${Y}]")
    trait W[X, Y]

    illTyped(
      "lazily[U[String]]",
      "(No U\\[String])|(could not find implicit value for parameter lv: => U\\[String])"
    )

    illTyped(
      "lazily[V]",
      "(could not find Lazy implicit value of type V)|(could not find implicit value for parameter lv: => V)"
    )

    illTyped(
      "lazily[W[String, Int]]",
      "(No W\\[String, Int])|(could not find implicit value for parameter lv: => W\\[String,Int])"
    )
  }

  @Test
  def testInteractionWithTaggedTypes: Unit = {
    import tag._

    class Readable[A]
    trait IdTag
    type Id = String @@ IdTag

    implicit def taggedStringReadable[T, M[_, _]](
      implicit ev: String @@ T =:= M[String, T]
    ): Readable[M[String, T]] = new Readable

    implicitly[Readable[Id]]
    //implicitly[=> Readable[Id]]
    implicitly[Readable[Id]]
  }
}
