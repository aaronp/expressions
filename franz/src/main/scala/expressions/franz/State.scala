package expressions.franz

import cats.{Monad, Semigroup}

case class State[S, A](run: S => (A, S)):
  def flatMap[B](f: A => State[S, B]): State[S, B] =
    State { s =>
      val (a, t) = run(s)
      f(a).run(t)
    }

  def map[B](f: A => B): State[S, B] =
    State { s =>
      val (a, t) = run(s)
      (f(a), t)
    }
//
//  def contramap[B](f : S => B) : State[B, A] =
//    val outter = this
//    State[B] { b =>
//      outter.run()
//    }
end State

object State:
  def of[S, A](value: A) = State[S, A](in => (value, in))

  def combine[S : Semigroup, A](state: S, value: A) = State[S, A](left => (value, summon[Semigroup[S]].combine(state, left)))

//  given generic[S]: Monad[[A] =>> State[S, A]] with
//    def pure[A](a: A): State[S, A] = State(s => (s, a))
//
//    def flatMap[A, B](fa: State[S, A])(f: A => State[S, B]) = fa flatMap f

end State
