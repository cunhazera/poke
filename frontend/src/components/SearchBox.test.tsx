import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { SearchBox } from './SearchBox'

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

/** A parent that applies each search late, like a router navigation rendered as a transition. */
function SlowParent({ applied }: { applied: string[] }) {
  const [value, setValue] = useState('')
  return (
    <SearchBox
      label="Search"
      placeholder=""
      value={value}
      onSearch={(text) => {
        applied.push(text)
        setTimeout(() => setValue(text), 200)
      }}
    />
  )
}

describe('SearchBox', () => {
  it('keeps what the user typed after an earlier search was sent but before it was applied', async () => {
    const user = userEvent.setup()
    const applied: string[] = []
    render(<SlowParent applied={applied} />)
    const box = screen.getByRole('searchbox', { name: 'Search' })

    await user.type(box, 'cha')
    await sleep(350) // the debounced 'cha' has been sent; the parent applies it at ~500 ms
    await user.type(box, 'r')
    await sleep(700) // 'cha' arrives, then the debounced 'char' is sent and applied

    expect(box).toHaveValue('char')
    expect(applied).toEqual(['cha', 'char'])
  })
})
