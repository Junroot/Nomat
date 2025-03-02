interface NavigationContent extends React.PropsWithChildren {}

const Column1: React.FC<NavigationContent> = ({children}) => {
     return <div
     className="flex-1 flex flex-col px-1 gap-4"
    >
        {children}
    </div>
}

export default Column1