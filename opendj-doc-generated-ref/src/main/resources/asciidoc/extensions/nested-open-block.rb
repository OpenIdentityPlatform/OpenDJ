# A custom block that allows open blocks to be nested using the
# example block container with the open block style.
#
# Usage:
#
#  [open]
#  ====
#  [open]
#  ======
#  nested
#  ======
#  ====
Asciidoctor::Extensions.register do
  block do
    named :open
    on_context :example
    process do |parent, reader, attrs|
      create_open_block parent, attrs
    end
  end
end